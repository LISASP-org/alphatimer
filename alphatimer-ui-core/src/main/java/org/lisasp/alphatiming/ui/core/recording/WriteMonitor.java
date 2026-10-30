package org.lisasp.alphatiming.ui.core.recording;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatRepository;
import org.lisasp.alphatiming.legacy.server.LaneTime;
import org.lisasp.alphatiming.legacy.server.LaneTimeRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
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

    HeatRepository heats(HeatRepository store) {
        return new HeatRepository() {
            @Override
            public Optional<HeatDto> find(int event, int heat) {
                return store.find(event, heat);
            }

            @Override
            public SortedSet<Integer> findUsedLanes() {
                return store.findUsedLanes();
            }

            @Override
            public List<HeatDto> findRecent(int count) {
                return store.findRecent(count);
            }

            @Override
            public List<HeatDto> findAll() {
                return store.findAll();
            }

            @Override
            public HeatDto create(HeatDto dto) {
                return write(RecordedData.HEATS, () -> store.create(dto));
            }

            @Override
            public void save(HeatDto dto) {
                write(RecordedData.HEATS, () -> store.save(dto));
            }
        };
    }

    LaneTimeRepository laneTimes(LaneTimeRepository store) {
        return new LaneTimeRepository() {
            @Override
            public void save(LaneTime laneTime) {
                write(RecordedData.LANE_TIMES, () -> store.save(laneTime));
            }

            @Override
            public List<LaneTime> findAll() {
                return store.findAll();
            }

            @Override
            public void delete(int event, int heatNumber, int laneIndex, long timeInMillis) {
                write(RecordedData.LANE_TIMES, () -> store.delete(event, heatNumber, laneIndex, timeInMillis));
            }
        };
    }
}
