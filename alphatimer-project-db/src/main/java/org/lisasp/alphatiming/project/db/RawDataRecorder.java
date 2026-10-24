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

@Slf4j
public final class RawDataRecorder implements ByteConsumer, AutoCloseable {

    private final ProjectDatabase db;
    private final DateTimeFacade time;
    private final int flushBytes;
    private final Consumer<RuntimeException> onError;
    private final ScheduledExecutorService flusher;

    private final Object lock = new Object();
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean failing;

    /** Bytes that cannot be stored stay buffered until the store works again; only the first failure in a row is reported. */
    public RawDataRecorder(ProjectDatabase db, DateTimeFacade time, int flushBytes, long flushMillis, Consumer<RuntimeException> onError) {
        this.db = db;
        this.time = time;
        this.flushBytes = flushBytes;
        this.onError = onError;
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
                buffer = new ByteArrayOutputStream();
                failing = false;
            } catch (RuntimeException ex) {
                if (failing) {
                    log.debug("Raw data still cannot be stored; {} bytes buffered.", data.length);
                } else {
                    failing = true;
                    log.warn("Could not store raw data; keeping it buffered until the store works again.", ex);
                    onError.accept(ex);
                }
            }
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
        flush();
    }

    public static List<byte[]> readAll(ProjectDatabase db) {
        return db.read(em -> em.createQuery("select r from RawDataChunkEntity r order by r.id", RawDataChunkEntity.class)
                               .getResultList()
                               .stream()
                               .map(RawDataChunkEntity::getData)
                               .toList());
    }
}
