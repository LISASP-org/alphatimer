package org.lisasp.alphatiming.ui.core.recording;

import org.lisasp.alphatiming.project.db.MessageStore;
import org.lisasp.alphatiming.project.db.RawDataRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.basics.notification.primitive.ByteConsumer;
import org.lisasp.alphatiming.project.db.ProjectDatabase;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatimer.serial.com.NrSerialReader;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.SerialMode;
import org.lisasp.alphatiming.ui.core.settings.Settings;
import org.lisasp.alphatiming.ui.core.settings.SettingsProblem;
import org.lisasp.alphatimer.serial.tcp.TcpReader;
import org.lisasp.alphatiming.project.db.testdata.DataHandlingMessageTestData;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RecordingSessionTest {

    static final class FakeReader implements SerialPortReader {
        final List<ByteConsumer> consumers = new ArrayList<>();
        int starts;
        int closes;

        @Override
        public SerialPortReader register(ByteConsumer listener) {
            consumers.add(listener);
            return this;
        }

        @Override
        public void start() {
            starts++;
        }

        @Override
        public void close() {
            closes++;
        }

        void push(byte... data) {
            for (byte b : data) {
                consumers.forEach(c -> c.accept(b));
            }
        }
    }

    @TempDir
    Path dir;

    private ProjectDatabase db;
    private final List<FakeReader> created = new ArrayList<>();
    private RecordingSession session;

    private static Settings settings(int restPort) {
        return new Settings("localhost", 8585, 60, restPort).withCompetitionKey("WK");
    }

    @BeforeEach
    void setUp() throws Exception {
        db = ProjectDatabase.createNew(dir);
        session = new RecordingSession(db, s -> {
            FakeReader r = new FakeReader();
            created.add(r);
            return r;
        }, () -> LocalDateTime.of(2021, 6, 21, 14, 53));
    }

    @AfterEach
    void tearDown() {
        session.close();
        db.closeAndDelete();
    }

    private FakeReader reader() {
        return created.get(created.size() - 1);
    }

    @Test
    void processesMessagesStoresRawDataAndReportsHeats() {
        List<org.lisasp.alphatiming.heats.LiveHeats> heats = new CopyOnWriteArrayList<>();
        session.addHeatListener(heats::add);
        session.start(settings(0));
        assertThat(session.state()).isEqualTo(RecordingState.RUNNING);
        assertThat(reader().starts).isEqualTo(1);

        reader().push(DataHandlingMessageTestData.message1);
        reader().push(DataHandlingMessageTestData.message2);
        session.stop();

        assertThat(session.state()).isEqualTo(RecordingState.STOPPED);
        assertThat(new MessageStore(db).findBy("", (short) 1, (byte) 1)).hasSize(1);
        int expected = DataHandlingMessageTestData.message1.length + DataHandlingMessageTestData.message2.length;
        assertThat(RawDataRecorder.readAll(db).stream().mapToInt(c -> c.length).sum()).isEqualTo(expected);
        assertThat(heats).isNotEmpty();
    }

    @Test
    void invalidSettingsGiveErrorStateWithoutCreatingAReader() {
        List<RecordingState> states = new ArrayList<>();
        session.addStateListener(states::add);
        session.start(new Settings(" ", 8585, 60, 0).withCompetitionKey("WK"));
        assertThat(session.state()).isEqualTo(RecordingState.ERROR);
        assertThat(session.error().kind()).isEqualTo(RecordingError.Kind.INVALID_SETTINGS);
        assertThat(session.error().problems()).containsExactly(SettingsProblem.TCP_HOST_EMPTY);
        assertThat(created).isEmpty();
        assertThat(states).containsExactly(RecordingState.ERROR);
    }

    @Test
    void restPortInUseGivesErrorAndClosesTheReader() throws Exception {
        try (ServerSocket blocker = new ServerSocket(0)) {
            session.start(settings(blocker.getLocalPort()));
            assertThat(session.state()).isEqualTo(RecordingState.ERROR);
            assertThat(session.error().kind()).isEqualTo(RecordingError.Kind.REST_PORT);
            assertThat(session.error().detail()).isEqualTo(String.valueOf(blocker.getLocalPort()));
            assertThat(reader().closes).isEqualTo(1);
            assertThat(reader().starts).isZero();
        }
    }

    @Test
    void canBeStartedAndStoppedRepeatedlyOnTheSameRestPort() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        for (int i = 0; i < 3; i++) {
            session.start(settings(port));
            assertThat(session.state()).isEqualTo(RecordingState.RUNNING);
            session.stop();
            assertThat(session.state()).isEqualTo(RecordingState.STOPPED);
        }
        assertThat(created).hasSize(3);
        assertThat(created).allSatisfy(r -> {
            assertThat(r.starts).isEqualTo(1);
            assertThat(r.closes).isEqualTo(1);
        });
    }

    @Test
    void garbageDoesNotStopProcessing() {
        session.start(settings(0));
        byte[] garbage = new byte[3000];
        for (int i = 0; i < garbage.length; i++) {
            garbage[i] = (byte) (i * 31 + 7);
        }
        reader().push(garbage);
        reader().push(DataHandlingMessageTestData.bogus);
        reader().push(DataHandlingMessageTestData.message1);
        reader().push(DataHandlingMessageTestData.message2);
        assertThat(session.state()).isEqualTo(RecordingState.RUNNING);
        assertThat(new MessageStore(db).findBy("", (short) 1, (byte) 1)).hasSize(1);
    }

    @Test
    void processingErrorsAreReportedToTheErrorListenersAndTheSessionKeepsRunning() {
        List<Exception> errors = new CopyOnWriteArrayList<>();
        session.addErrorListener(errors::add);
        session.start(settings(0));
        db.close();
        reader().push(DataHandlingMessageTestData.message1);
        reader().push(DataHandlingMessageTestData.message2);
        assertThat(errors).isNotEmpty();
        assertThat(session.state()).isEqualTo(RecordingState.RUNNING);
    }

    @Test
    void messageListenersGetInterpretedMessagesAndPingsOnlyGoToThePingListeners() {
        List<org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage> messages = new CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicInteger pings = new java.util.concurrent.atomic.AtomicInteger();
        session.addMessageListener(messages::add);
        session.addPingListener(pings::incrementAndGet);
        session.start(settings(0));

        reader().push(DataHandlingMessageTestData.ping);
        assertThat(pings.get()).isEqualTo(1);
        assertThat(messages).isEmpty();

        reader().push(DataHandlingMessageTestData.message1);
        assertThat(messages).as("the first half of a message is not a message yet").isEmpty();
        reader().push(DataHandlingMessageTestData.message2);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getEvent()).isEqualTo((short) 1);
        assertThat(messages.get(0).getHeat()).isEqualTo((byte) 1);

        reader().push(DataHandlingMessageTestData.ping);
        assertThat(pings.get()).isEqualTo(2);
        assertThat(messages).hasSize(1);
    }

    @Test
    void failingListenerDoesNotEscapeIntoTheReaderThread() {
        session.addMessageListener(m -> {
            throw new IllegalStateException("listener bug");
        });
        session.start(settings(0));
        reader().push(DataHandlingMessageTestData.message1);
        reader().push(DataHandlingMessageTestData.message2);
        assertThat(session.state()).isEqualTo(RecordingState.RUNNING);
    }

    @Test
    void aDisabledServerDoesNotOpenItsPort() throws Exception {
        try (ServerSocket blocker = new ServerSocket(0)) {
            session.start(new Settings("localhost", 8585, 60, 1).withCompetitionKey("WK").withRest(blocker.getLocalPort(), false));
            assertThat(session.state()).isEqualTo(RecordingState.RUNNING);
        }
    }

    @Test
    void replayingAFileProcessesItAtTheSerialRateAndStopsTheRecordingAtTheEnd() throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        bytes.writeBytes(DataHandlingMessageTestData.message1);
        bytes.writeBytes(DataHandlingMessageTestData.message2);
        Path data = dir.resolve("run.serial");
        java.nio.file.Files.write(data, bytes.toByteArray());
        RecordingSession replaying = new RecordingSession(db, RecordingSession.standard(), () -> LocalDateTime.of(2021, 6, 21, 14, 53));
        List<RecordingState> states = new CopyOnWriteArrayList<>();
        replaying.addStateListener(states::add);

        replaying.start(new Settings("h", 1, 60, 0).withCompetitionKey("WK").withConnectionType(ConnectionType.FILE).withReplayFile(data.toString()));

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> assertThat(replaying.state()).isEqualTo(RecordingState.STOPPED));
        assertThat(states).containsExactly(RecordingState.RUNNING, RecordingState.STOPPED);
        assertThat(new MessageStore(db).findBy("", (short) 1, (byte) 1)).hasSize(1);
        assertThat(RawDataRecorder.readAll(db).stream().mapToInt(c -> c.length).sum()).isEqualTo(bytes.size());
    }

    @Test
    void aMissingReplayFileEndsInTheErrorState() {
        RecordingSession replaying = new RecordingSession(db, RecordingSession.standard(), () -> LocalDateTime.now());
        replaying.start(new Settings("h", 1, 60, 0).withCompetitionKey("WK").withConnectionType(ConnectionType.FILE).withReplayFile(dir.resolve("nope.serial").toString()));
        assertThat(replaying.state()).isEqualTo(RecordingState.ERROR);
        assertThat(replaying.error().kind()).isEqualTo(RecordingError.Kind.REPLAY_FILE_NOT_FOUND);
        assertThat(replaying.error().detail()).endsWith("nope.serial");
    }

    @Test
    void standardReadersFollowTheConnectionType() {
        RecordingSession.ReaderFactory factory = RecordingSession.standard();
        assertThat(factory.create(new Settings("h", 1, 60, 0).withCompetitionKey("WK"))).isInstanceOf(TcpReader.class);
        assertThat(factory.create(new Settings("h", 1, 60, 0).withCompetitionKey("WK").withConnectionType(ConnectionType.FILE).withReplayFile("x"))).isInstanceOf(org.lisasp.alphatiming.project.db.replay.FileReplayReader.class);
        assertThat(factory.create(new Settings(ConnectionType.SERIAL, "", 1, "COM3", SerialMode.ARES21, 60, 1999, false).withCompetitionKey("WK"))).isInstanceOf(NrSerialReader.class);
    }

    @Test
    void serialPortProblemsEndInTheErrorState() {
        RecordingSession real = new RecordingSession(db, RecordingSession.standard(), () -> LocalDateTime.now());
        real.start(new Settings(ConnectionType.SERIAL, "", 1, "/dev/does-not-exist-42", SerialMode.ARES21, 60, 1999, false).withCompetitionKey("WK"));
        assertThat(real.state()).isEqualTo(RecordingState.ERROR);
        assertThat(real.error().kind()).isEqualTo(RecordingError.Kind.SERIAL_PORT_NOT_FOUND);
        assertThat(real.error().detail()).isEqualTo("/dev/does-not-exist-42");
        real.close();
    }

    @Test
    void stopStoresEveryByteTheTcpReaderReceivedBeforeTheRecordingEnds() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            ByteArrayOutputStream delivered = new ByteArrayOutputStream();
            CountDownLatch firstByteArrived = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            RecordingSession tcp = new RecordingSession(db, s -> {
                TcpReader r = new TcpReader(s.tcpHost(), s.tcpPort());
                r.register(b -> {
                    synchronized (delivered) {
                        delivered.write(b);
                    }
                    if (firstByteArrived.getCount() > 0) {
                        firstByteArrived.countDown();
                        awaitIgnoringInterrupts(release);
                    }
                });
                return r;
            }, () -> LocalDateTime.of(2021, 6, 21, 14, 53));
            tcp.start(new Settings("localhost", server.getLocalPort(), 60, 0).withCompetitionKey("WK"));
            assertThat(tcp.state()).isEqualTo(RecordingState.RUNNING);

            try (Socket device = server.accept()) {
                OutputStream out = device.getOutputStream();
                out.write(DataHandlingMessageTestData.message1);
                out.flush();
                assertThat(firstByteArrived.await(10, TimeUnit.SECONDS)).isTrue();

                CompletableFuture<Void> stopped = CompletableFuture.runAsync(tcp::stop);
                CompletableFuture.anyOf(stopped, CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS))).join();
                release.countDown();
                stopped.get(10, TimeUnit.SECONDS);
            }

            ByteArrayOutputStream stored = new ByteArrayOutputStream();
            RawDataRecorder.readAll(db).forEach(stored::writeBytes);
            synchronized (delivered) {
                assertThat(stored.toByteArray()).isNotEmpty().isEqualTo(delivered.toByteArray());
            }
            assertThat(tcp.state()).isEqualTo(RecordingState.STOPPED);
        }
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
