package org.lisasp.alphatiming.heats;

/**
 * @param timeInMillis the latest intermediate time, or the final time when {@code finished}
 * @param place        0 = no placing (yet)
 */
public record LaneDto(int number, int timeInMillis, int lap, LaneStatus status, Penalty penalty, int place, boolean finished) {
}
