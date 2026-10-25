package org.lisasp.alphatiming.ui.core.project;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public final class AutoSaver implements AutoCloseable {

    private final Supplier<TimingDocument> current;
    private final Consumer<Exception> onError;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auto-saver");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> task;
    private final ReentrantLock running = new ReentrantLock();
    private int pauses;

    public AutoSaver(Supplier<TimingDocument> current, Consumer<Exception> onError) {
        this.current = current;
        this.onError = onError;
    }

    public synchronized void setIntervalSeconds(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("Auto-save interval must be positive: " + seconds);
        }
        if (task != null) {
            task.cancel(false);
        }
        task = executor.scheduleWithFixedDelay(this::runOnce, seconds, seconds, TimeUnit.SECONDS);
    }

    /** Waits for a running auto-save; until {@link #resume()} no auto-save writes. Pauses nest. */
    public void pause() {
        running.lock();
        try {
            pauses++;
        } finally {
            running.unlock();
        }
    }

    public void resume() {
        running.lock();
        try {
            if (pauses == 0) {
                throw new IllegalStateException("The auto-saver is not paused.");
            }
            pauses--;
        } finally {
            running.unlock();
        }
    }

    public void runOnce() {
        try {
            saveUnlessPaused();
        } catch (Exception ex) {
            log.warn("Auto-save failed", ex);
            try {
                onError.accept(ex);
            } catch (RuntimeException failure) {
                log.warn("Auto-save error callback failed", failure);
            }
        }
    }

    private void saveUnlessPaused() throws IOException {
        running.lock();
        try {
            TimingDocument doc = current.get();
            if (pauses == 0 && doc != null) {
                doc.saveIfChanged();
            }
        } finally {
            running.unlock();
        }
    }

    @Override
    public synchronized void close() {
        executor.shutdownNow();
    }
}
