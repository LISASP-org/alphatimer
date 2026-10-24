package org.lisasp.alphatiming.ui.core.project;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public final class AutoSaver implements AutoCloseable {

    private final Supplier<ProjectDocument> current;
    private final Consumer<Exception> onError;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auto-saver");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> task;

    public AutoSaver(Supplier<ProjectDocument> current, Consumer<Exception> onError) {
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

    void runOnce() {
        try {
            ProjectDocument doc = current.get();
            if (doc != null && doc.isDirty() && doc.file().isPresent()) {
                doc.save();
            }
        } catch (Exception ex) {
            log.warn("Auto-save failed", ex);
            try {
                onError.accept(ex);
            } catch (RuntimeException failure) {
                log.warn("Auto-save error callback failed", failure);
            }
        }
    }

    @Override
    public synchronized void close() {
        executor.shutdownNow();
    }
}
