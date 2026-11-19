package org.lisasp.alphatiming.heats;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * @param readyToStart         arrival time of the message that announced the heat; null when the heat was never announced
 * @param lastMessage          arrival time of the last message that was applied to the heat; null when unknown (older project files)
 * @param startTimeOfDayMillis time of day on the clock of the device that the start carried, as sent; null when unknown
 */
public record HeatDto(int event,
                      int heat,
                      HeatStatus status,
                      int lapCount,
                      LocalDateTime readyToStart,
                      LocalDateTime started,
                      LocalDateTime ended,
                      LocalDateTime lastMessage,
                      Integer startTimeOfDayMillis,
                      List<LaneDto> lanes) {

    public static HeatDto empty(int event, int heat, int laneCount) {
        List<LaneDto> lanes = IntStream.rangeClosed(1, laneCount)
                                       .mapToObj(number -> new LaneDto(number, LaneStatus.NotUsed, Set.of(), 0, List.of()))
                                       .toList();
        return new HeatDto(event, heat, HeatStatus.Open, 0, null, null, null, null, null, lanes);
    }
}
