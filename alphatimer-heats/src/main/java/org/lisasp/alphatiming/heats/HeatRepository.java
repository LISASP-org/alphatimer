package org.lisasp.alphatiming.heats;

import java.util.List;
import java.util.Optional;
import java.util.SortedSet;

public interface HeatRepository {

    Optional<HeatDto> find(int event, int heat);

    SortedSet<Integer> findUsedLanes();

    /** The most recently changed heats, newest first. */
    List<HeatDto> findRecent(int count);

    List<HeatDto> findAll();

    HeatDto create(HeatDto dto);

    void save(HeatDto dto);
}
