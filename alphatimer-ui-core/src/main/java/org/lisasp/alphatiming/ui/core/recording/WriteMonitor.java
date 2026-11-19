package org.lisasp.alphatiming.ui.core.recording;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
final class WriteMonitor {

    private final List<WriteListener> listeners;
    private final Set<RecordedData> failing = ConcurrentHashMap.newKeySet();

    WriteMonitor(List<WriteListener> listeners) {
        this.listeners = listeners;
    }

    void write(RecordedData data, Runnable write) {
        write(data, () -> {
            write.run();
            return null;
        });
    }

    <T> T write(RecordedData data, Supplier<T> write) {
        T result;
        try {
            result = write.get();
        } catch (RuntimeException ex) {
            throw new RecordingWriteException(data, ex);
        }
        stored(data);
        return result;
    }

    void failed(RecordedData data, Exception cause) {
        failing.add(data);
        for (WriteListener listener : listeners) {
            try {
                listener.writeFailed(data, cause);
            } catch (RuntimeException ex) {
                log.warn("Write listener failed", ex);
            }
        }
    }

    void stored(RecordedData data) {
        if (!failing.remove(data)) {
            return;
        }
        for (WriteListener listener : listeners) {
            try {
                listener.writeRecovered(data);
            } catch (RuntimeException ex) {
                log.warn("Write listener failed", ex);
            }
        }
    }

    void dropped(RecordedData data, int bytes) {
        for (WriteListener listener : listeners) {
            try {
                listener.dataDropped(data, bytes);
            } catch (RuntimeException ex) {
                log.warn("Write listener failed", ex);
            }
        }
    }
}
