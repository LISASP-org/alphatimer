package org.lisasp.alphatiming.legacy.server;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryLaneTimeRepository implements LaneTimeRepository {

    private final List<LaneTime> laneTimes = new ArrayList<>();

    @Override
    public synchronized void save(LaneTime laneTime) {
        laneTimes.add(laneTime);
    }

    @Override
    public synchronized List<LaneTime> findAll() {
        return List.copyOf(laneTimes);
    }

    @Override
    public synchronized void delete(int event, int heatNumber, String round, int laneIndex, long timeInMillis) {
        laneTimes.removeIf(t -> t.event() == event && t.heatNumber() == heatNumber && t.round().equals(round) && t.laneIndex() == laneIndex && t.timeInMillis() == timeInMillis);
    }
}
