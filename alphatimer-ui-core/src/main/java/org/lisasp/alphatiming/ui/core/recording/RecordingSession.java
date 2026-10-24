package org.lisasp.alphatiming.ui.core.recording;

import org.lisasp.alphatiming.project.db.MessageStore;
import org.lisasp.alphatiming.project.db.RawDataRecorder;
import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageAggregator;
import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageListener;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.Ping;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageAggregator;
import org.lisasp.alphatimer.ares.serial.MessageConverter;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;
import org.lisasp.alphatimer.serial.tcp.TcpReader;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.basics.notification.primitive.ByteConsumer;
import org.lisasp.alphatiming.project.db.TimingDatabase;
import org.lisasp.alphatiming.heats.LiveHeats;
import org.lisasp.alphatiming.heats.HeatService;
import org.lisasp.alphatiming.project.db.HeatStore;
import org.lisasp.alphatiming.project.db.LaneTimeStore;
import org.lisasp.alphatiming.legacy.server.LegacyHttpServer;
import org.lisasp.alphatiming.legacy.server.LegacyService;
import org.lisasp.alphatiming.legacy.server.dto.Heat;
import org.lisasp.alphatiming.project.db.replay.FileReplayReader;
import org.lisasp.alphatiming.project.db.replay.ReplayFileException;
import org.lisasp.alphatimer.serial.com.NrSerialReader;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatimer.serial.com.SerialConnectionException;
import org.lisasp.alphatiming.ui.core.settings.Settings;
import org.lisasp.alphatiming.ui.core.settings.SettingsProblem;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * One recording at a time. {@link #start} builds a fresh pipeline from a settings snapshot, {@link #stop} tears it down,
 * so the settings can be changed between recordings without restarting the program.
 */
@Slf4j
public final class RecordingSession implements AutoCloseable {

    public interface ReaderFactory {
        SerialPortReader create(Settings settings);
    }

    /** The copied pipeline tags every message with a competition; a project holds exactly one, so the tag is constant. */
    private static final String COMPETITION = "";
    private static final int REPLAY_BAUD_RATE = 9600;

    private final TimingDatabase db;
    private final ReaderFactory readers;
    private final DateTimeFacade time;

    private final List<Consumer<RecordingState>> stateListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<LiveHeats>> heatListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<DataHandlingMessage>> messageListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> pingListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Exception>> errorListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> connectionLostListeners = new CopyOnWriteArrayList<>();
    private final List<WriteListener> writeListeners = new CopyOnWriteArrayList<>();
    private final WriteMonitor writes = new WriteMonitor(writeListeners);

    private volatile RecordingState state = RecordingState.STOPPED;
    private volatile RecordingError error;

    private volatile SerialPortReader reader;
    private RawDataRecorder rawRecorder;
    private LegacyHttpServer restServer;
    private volatile LegacyService legacy;

    public RecordingSession(TimingDatabase db, ReaderFactory readers, DateTimeFacade time) {
        this.db = db;
        this.readers = readers;
        this.time = time;
    }

    /** TCP or serial reader, depending on the connection type of the settings. */
    public static ReaderFactory standard() {
        return s -> switch (s.connectionType()) {
            case SERIAL -> new NrSerialReader(s.serialPort(), s.serialMode().configuration());
            case FILE -> new FileReplayReader(Path.of(s.replayFile()), REPLAY_BAUD_RATE);
            case TCP -> new TcpReader(s.tcpHost(), s.tcpPort());
        };
    }

    public static ReaderFactory tcp() {
        return s -> new TcpReader(s.tcpHost(), s.tcpPort());
    }

    public synchronized void start(Settings snapshot) {
        if (state == RecordingState.RUNNING) {
            return;
        }
        List<SettingsProblem> problems = snapshot.validate();
        if (!problems.isEmpty()) {
            fail(new RecordingError(RecordingError.Kind.INVALID_SETTINGS, "", problems));
            return;
        }
        try {
            build(snapshot);
            reader.start();
            log.info("Recording started ({})", snapshot.connectionType());
            setState(RecordingState.RUNNING, null);
        } catch (RuntimeException ex) {
            log.warn("Could not start recording", ex);
            teardown();
            fail(toError(ex));
        }
    }

    private static RecordingError toError(RuntimeException ex) {
        if (ex instanceof RestServerStartException rest) {
            return new RecordingError(RecordingError.Kind.REST_PORT, String.valueOf(rest.port()));
        }
        if (ex instanceof ReplayFileException replay) {
            RecordingError.Kind kind = replay.reason() == ReplayFileException.Reason.NOT_FOUND ? RecordingError.Kind.REPLAY_FILE_NOT_FOUND : RecordingError.Kind.REPLAY_FILE_UNREADABLE;
            return new RecordingError(kind, replay.file().toString());
        }
        if (ex instanceof SerialConnectionException serial) {
            RecordingError.Kind kind = switch (serial.reason()) {
                case NOT_FOUND -> RecordingError.Kind.SERIAL_PORT_NOT_FOUND;
                case IN_USE -> RecordingError.Kind.SERIAL_PORT_IN_USE;
                case UNAVAILABLE -> RecordingError.Kind.SERIAL_UNAVAILABLE;
                case OPEN_FAILED -> RecordingError.Kind.SERIAL_OPEN_FAILED;
                case NAME_TOO_LONG -> RecordingError.Kind.SERIAL_PORT_NAME_TOO_LONG;
            };
            return new RecordingError(kind, serial.port());
        }
        return new RecordingError(RecordingError.Kind.UNEXPECTED, ex.getMessage() == null ? ex.toString() : ex.getMessage());
    }

    private void build(Settings s) {
        legacy = new LegacyService(writes.laneTimes(new LaneTimeStore(db)));
        MessageStore messageStore = new MessageStore(db);
        HeatService heatService = new HeatService(writes.heats(new HeatStore(db)), this::reportError);
        heatListeners.forEach(heatService::register);
        rawRecorder = new RawDataRecorder(db, time, 512, 250, ex -> writes.failed(RecordedData.RAW_DATA, ex), () -> writes.stored(RecordedData.RAW_DATA));

        InputCollector collector = new InputCollector(COMPETITION, time);
        MessageConverter converter = new MessageConverter();
        DataHandlingMessageAggregator aggregator = new MessageAggregator();
        DataHandlingMessageRefiner refiner = new DataHandlingMessageRefiner();

        aggregator.register(isolated(legacy));
        aggregator.register(isolated(m -> writes.write(RecordedData.MESSAGES, () -> messageStore.put(m))));
        aggregator.register(refiner);
        aggregator.register(this::announce);
        refiner.register(heatService::accept);
        converter.register(event -> {
            if (event instanceof Ping) {
                announcePing();
            }
            aggregator.accept(event);
        });
        collector.register(converter);

        SerialPortReader created = readers.create(s);
        reader = created;
        reader.register(safe(rawRecorder)).register(safe(collector));
        reader.addConnectionLostListener(() -> connectionLost(created, s.serialPort()));
        if (reader instanceof FileReplayReader replay) {
            replay.addFinishedListener(this::stop);
        }

        if (s.restEnabled()) {
            try {
                restServer = new LegacyHttpServer(s.restPort(), legacy::getHeats);
            } catch (java.io.IOException | RuntimeException ex) {
                throw new RestServerStartException(s.restPort(), ex);
            }
        }
    }

    /** A failure while processing one byte must never end the reader thread. */
    private ByteConsumer safe(ByteConsumer delegate) {
        return b -> {
            try {
                delegate.accept(b);
            } catch (RuntimeException ex) {
                log.warn("Error while processing received data", ex);
                reportError(ex);
            }
        };
    }

    /** A store that cannot write a message must not keep it from the stores registered after it. */
    private DataHandlingMessageListener isolated(DataHandlingMessageListener delegate) {
        return m -> {
            try {
                delegate.accept(m);
            } catch (RuntimeException ex) {
                log.warn("Could not store received message", ex);
                reportError(ex);
            }
        };
    }

    private void reportError(RuntimeException ex) {
        if (ex instanceof RecordingWriteException write) {
            writes.failed(write.data(), (Exception) write.getCause());
            return;
        }
        for (Consumer<Exception> listener : errorListeners) {
            try {
                listener.accept(ex);
            } catch (RuntimeException failure) {
                log.warn("Error listener failed", failure);
            }
        }
    }

    /** Tells the message listeners; a failing listener must not affect the pipeline or the other listeners. */
    private void announce(DataHandlingMessage message) {
        for (Consumer<DataHandlingMessage> listener : messageListeners) {
            try {
                listener.accept(message);
            } catch (RuntimeException ex) {
                log.warn("Message listener failed", ex);
            }
        }
    }

    private void announcePing() {
        for (Runnable listener : pingListeners) {
            try {
                listener.run();
            } catch (RuntimeException ex) {
                log.warn("Ping listener failed", ex);
            }
        }
    }

    public synchronized void stop() {
        if (state != RecordingState.RUNNING) {
            return;
        }
        teardown();
        log.info("Recording stopped");
        setState(RecordingState.STOPPED, null);
    }

    private synchronized void connectionLost(SerialPortReader lost, String port) {
        if (lost != reader || state != RecordingState.RUNNING) {
            return;
        }
        stop();
        for (Consumer<String> listener : connectionLostListeners) {
            try {
                listener.accept(port);
            } catch (RuntimeException ex) {
                log.warn("Connection lost listener failed", ex);
            }
        }
    }

    private void teardown() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (RuntimeException ex) {
            log.warn("Could not close reader", ex);
        }
        try {
            if (restServer != null) {
                restServer.close();
            }
        } catch (RuntimeException ex) {
            log.warn("Could not close REST server", ex);
        }
        try {
            if (rawRecorder != null) {
                rawRecorder.close();
            }
        } catch (RuntimeException ex) {
            log.warn("Could not close raw data recorder", ex);
        }
        reader = null;
        restServer = null;
        rawRecorder = null;
    }

    private void fail(RecordingError failure) {
        setState(RecordingState.ERROR, failure);
    }

    private void setState(RecordingState newState, RecordingError failure) {
        error = failure;
        state = newState;
        stateListeners.forEach(l -> l.accept(newState));
    }

    public RecordingState state() {
        return state;
    }

    /** The reason of the ERROR state; null in every other state. */
    public RecordingError error() {
        return error;
    }

    public void addErrorListener(Consumer<Exception> listener) {
        errorListeners.add(listener);
    }

    public void addWriteListener(WriteListener listener) {
        writeListeners.add(listener);
    }

    public boolean isConnected() {
        SerialPortReader r = reader;
        return state == RecordingState.RUNNING && r != null && r.isConnected();
    }

    public void addStateListener(Consumer<RecordingState> listener) {
        stateListeners.add(listener);
    }

    public void addHeatListener(Consumer<LiveHeats> listener) {
        heatListeners.add(listener);
    }

    /** Called with every interpreted message (the two raw lines of the device combined); pings are not messages. */
    public void addMessageListener(Consumer<DataHandlingMessage> listener) {
        messageListeners.add(listener);
    }

    public void addPingListener(Runnable listener) {
        pingListeners.add(listener);
    }

    /** Called with the serial port after a lost connection has stopped the recording. */
    public void addConnectionLostListener(Consumer<String> listener) {
        connectionLostListeners.add(listener);
    }

    public Heat[] legacyHeats() {
        LegacyService l = legacy;
        return l == null ? new Heat[0] : l.getHeats();
    }

    @Override
    public void close() {
        stop();
    }
}
