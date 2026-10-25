package org.lisasp.alphatiming.legacy.server;

import java.util.List;

public interface LaneTimeRepository {

    void save(LaneTime laneTime);

    List<LaneTime> findAll();

    void delete(int event, int heatNumber, int laneIndex, long timeInMillis);
}
