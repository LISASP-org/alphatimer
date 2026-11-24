package org.lisasp.alphatiming.ui.common;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Hands values to a UI thread, at most one task at a time: while a task waits, newer values only replace the one it will
 * show. A burst of heat changes (a LENEX file brings thousands at once) then costs a few refreshes instead of one each.
 */
public final class LatestOnly<T> {

    private record Value<T>(T value) {
    }

    private final Executor uiThread;
    private final Consumer<T> show;
    private final AtomicReference<Value<T>> latest = new AtomicReference<>();
    private final AtomicBoolean pending = new AtomicBoolean();

    public LatestOnly(Executor uiThread, Consumer<T> show) {
        this.uiThread = uiThread;
        this.show = show;
    }

    public void submit(T value) {
        latest.set(new Value<>(value));
        if (pending.compareAndSet(false, true)) {
            uiThread.execute(this::showLatest);
        }
    }

    private void showLatest() {
        pending.set(false);
        Value<T> value = latest.getAndSet(null);
        if (value != null) {
            show.accept(value.value());
        }
    }
}
