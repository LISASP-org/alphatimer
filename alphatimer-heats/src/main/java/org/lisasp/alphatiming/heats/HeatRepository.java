package org.lisasp.alphatiming.heats;

import java.util.List;
import java.util.Optional;
import java.util.SortedSet;

public interface HeatRepository {

    Optional<HeatDto> find(int event, int heat);

    SortedSet<Integer> findUsedLanes();

    /** Every heat, the most recently changed first. */
    List<HeatDto> findAll();

    HeatDto create(HeatDto dto);

    void save(HeatDto dto);
}
