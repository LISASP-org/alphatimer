package org.lisasp.alphatiming.project.db;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.basics.notification.primitive.ByteConsumer;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

@Slf4j
public final class RawDataRecorder implements ByteConsumer, AutoCloseable {

    private final TimingDatabase db;
    private final DateTimeFacade time;
    private final int flushBytes;
    private final Consumer<RuntimeException> onError;
    private final Runnable onStored;
    private final IntConsumer onDropped;
    private final ScheduledExecutorService flusher;

    private final Object lock = new Object();
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean failing;
    private boolean closed;
    private boolean refusalLogged;

    /**
     * Bytes that cannot be stored stay buffered until the store works again; only the first failure in a row is reported.
     * Bytes still unstored when it closes are discarded and their number goes to {@code onDropped}.
     */
    public RawDataRecorder(TimingDatabase db, DateTimeFacade time, int flushBytes, long flushMillis, Consumer<RuntimeException> onError, Runnable onStored,
                           IntConsumer onDropped) {
        this.db = db;
        this.time = time;
        this.flushBytes = flushBytes;
        this.onError = onError;
        this.onStored = onStored;
        this.onDropped = onDropped;
        this.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "raw-data-flusher");
            t.setDaemon(true);
            return t;
        });
        this.flusher.scheduleWithFixedDelay(this::flush, flushMillis, flushMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void accept(byte b) {
        synchronized (lock) {
            if (closed) {
                refuse();
                return;
            }
            buffer.write(b);
            if (buffer.size() >= flushBytes) {
                flush();
            }
        }
    }

    public void flush() {
        synchronized (lock) {
            if (buffer.size() == 0) {
                return;
            }
            byte[] data = buffer.toByteArray();
            try {
                db.write(em -> {
                    em.persist(new RawDataChunkEntity(time.now(), data));
                    return null;
                });
            } catch (RuntimeException ex) {
                if (failing) {
                    log.debug("Raw data still cannot be stored; {} bytes buffered.", data.length);
                } else {
                    failing = true;
                    log.warn("Could not store raw data; keeping it buffered until the store works again.", ex);
                    onError.accept(ex);
                }
                return;
            }
            buffer = new ByteArrayOutputStream();
            failing = false;
            onStored.run();
        }
    }

    private void refuse() {
        if (!refusalLogged) {
            refusalLogged = true;
            log.warn("Raw data arrived after the recording stopped; it is not recorded.");
        }
    }

    int bufferedBytes() {
        synchronized (lock) {
            return buffer.size();
        }
    }

    @Override
    public void close() {
        flusher.shutdownNow();
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            flush();
            int dropped = buffer.size();
            if (dropped == 0) {
                return;
            }
            buffer = new ByteArrayOutputStream();
            log.warn("Discarding {} bytes of raw data that could not be stored.", dropped);
            onDropped.accept(dropped);
        }
    }

    public static List<byte[]> readAll(TimingDatabase db) {
        return db.read(em -> em.createQuery("select r from RawDataChunkEntity r order by r.id", RawDataChunkEntity.class)
                               .getResultList()
                               .stream()
                               .map(RawDataChunkEntity::getData)
                               .toList());
    }
}
