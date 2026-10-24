package org.lisasp.alphatiming.legacy.server;

import java.util.List;

/** Where {@link LegacyService} keeps the lane times. */
public interface LaneTimeRepository {

    void save(LaneTime laneTime);

    List<LaneTime> findAll();
}
