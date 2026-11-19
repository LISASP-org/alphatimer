package org.lisasp.alphatiming.heats;

import java.util.Set;

/**
 * @param timeInMillis the latest intermediate time, or the final time when {@code finished}
 * @param penalties    unmodifiable, empty for none
 * @param place        0 = no placing (yet)
 */
public record LaneDto(int number, int timeInMillis, int lap, LaneStatus status, Set<Penalty> penalties, int place, boolean finished) {

    public LaneDto {
        penalties = Set.copyOf(penalties);
    }
}
