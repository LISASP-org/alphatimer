package org.lisasp.alphatiming.ui.core.recording;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatiming.lenex.api.InvalidLenexException;
import org.lisasp.alphatiming.lenex.LenexDocuments;
import org.lisasp.alphatiming.lenex.api.LenexMeet;
import org.lisasp.alphatiming.lenex.LenexReader;
import org.lisasp.alphatiming.lenex.LenexRefiner;
import org.lisasp.alphatiming.project.db.LenexVersion;
import org.lisasp.alphatiming.project.db.LenexVersionStore;
import org.lisasp.basics.jre.date.DateTimeFacade;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Records a LENEX file: checks it at an interval, keeps each version that changed more than its export time and feeds the
 * refined messages between the versions to the heats. A file that is missing or cannot be read stops nothing.
 */
@Slf4j
final class LenexRecording implements AutoCloseable {

    interface Listener {
        /** The file is present and readable. */
        void signOfLife();

        void recorded(LenexChanges changes);
    }

    static final LenexMeet NO_MEET = new LenexMeet(OptionalInt.empty(), List.of());

    private record Seen(FileTime modified, long size) {
    }

    private final Path file;
    private final LenexVersionStore store;
    private final Consumer<RefinedMessage> heats;
    private final DateTimeFacade time;
    private final Listener listener;
    private final WriteMonitor writes;
    private final LenexRefiner refiner = new LenexRefiner("");

    private ScheduledExecutorService scheduler;
    private volatile boolean connected;
    private Seen seen;
    private byte[] lastVersion;
    private LenexMeet lastMeet = NO_MEET;

    LenexRecording(Path file, LenexVersionStore store, Consumer<RefinedMessage> heats, DateTimeFacade time, Listener listener, WriteMonitor writes) {
        this.file = file;
        this.store = store;
        this.heats = heats;
        this.time = time;
        this.listener = listener;
        this.writes = writes;
        store.findLast().ifPresent(last -> {
            lastVersion = last.xml();
            lastMeet = meetOf(lastVersion);
        }
    }

    void start(int intervalSeconds) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "lenex-recording");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::checkSafely, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    private void checkSafely() {
        try {
            check();
        } catch (RuntimeException ex) {
            log.warn("Could not check the LENEX file {}", file, ex);
        }
    }

    synchronized void check() {
        Seen now;
        byte[] content;
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            now = new Seen(attributes.lastModifiedTime(), attributes.size());
            if (now.equals(seen)) {
                alive();
                return;
            }
            content = Files.readAllBytes(file);
        } catch (IOException ex) {
            connected = false;
            return;
        }
        alive();
        byte[] version;
        try {
            version = LenexDocuments.oneLine(content);
        } catch (IOException ex) {
            log.debug("The LENEX file {} is not complete yet", file);
            return;
        }
        seen = now;
        try {
            if (lastVersion != null && LenexDocuments.sameExceptExportTime(lastVersion, version)) {
                return;
            }
            record(version, LenexReader.read(new ByteArrayInputStream(version)));
        } catch (IOException ex) {
            log.warn("The LENEX file {} holds no meet", file);
        }
    }

    private void alive() {
        connected = true;
        listener.signOfLife();
    }

    private void record(byte[] version, LenexMeet meet) {
        LocalDateTime arrival = time.now();
        List<RefinedMessage> messages = refiner.refine(lastMeet, meet, arrival);
        writes.write(RecordedData.LENEX_VERSIONS, () -> store.record(arrival, version, !messages.isEmpty()));
        lastVersion = version;
        lastMeet = meet;
        for (RefinedMessage message : messages) {
            heats.accept(message);
        }
        long heatCount = messages.stream().map(m -> m.getEvent() + "/" + m.getRound() + "/" + m.getHeat()).distinct().count();
        listener.recorded(new LenexChanges(messages.size(), (int) heatCount));
    }

    boolean isConnected() {
        return connected;
    }

    /** Feeds the recorded versions through the refiner again, each with its arrival time, as they were fed live. */
    static void replay(List<LenexVersion> versions, Consumer<RefinedMessage> heats) {
        LenexRefiner refiner = new LenexRefiner("");
        LenexMeet previous = NO_MEET;
        for (LenexVersion version : versions) {
            LenexMeet meet = meetOf(version.xml());
            refiner.refine(previous, meet, version.arrival()).forEach(heats);
            previous = meet;
        }
    }

    private static LenexMeet meetOf(byte[] version) {
        try {
            return LenexReader.read(new ByteArrayInputStream(version));
        } catch (IOException ex) {
            log.warn("A recorded LENEX version cannot be read again", ex);
            return NO_MEET;
        }
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("The LENEX recording did not stop within 5 s");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        connected = false;
    }
}
