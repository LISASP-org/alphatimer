package org.lisasp.alphatiming.heats;

import java.util.List;
import java.util.Optional;
import java.util.SortedSet;

/** Where {@link HeatService} keeps the heats. */
public interface HeatRepository {

    Optional<HeatDto> find(String competition, int event, int heat);

    /** Every lane number that is used in at least one stored heat. */
    SortedSet<Integer> findUsedLanes();

    /** The most recently changed heats, newest first. */
    List<HeatDto> findRecent(int count);

    HeatDto create(HeatDto dto);

    void save(HeatDto dto);
}
