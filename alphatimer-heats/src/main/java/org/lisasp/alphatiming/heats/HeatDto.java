package org.lisasp.alphatiming.heats;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

/**
 * @param lastMessage arrival time of the last message that was applied to the heat; null when unknown (older project files)
 */
public record HeatDto(int event,
                      int heat,
                      HeatStatus status,
                      LocalDateTime started,
                      LocalDateTime ended,
                      int lapCount,
                      List<LaneDto> lanes,
                      LocalDateTime lastMessage) {

    public HeatDto(int event, int heat, HeatStatus status, LocalDateTime started, LocalDateTime ended, int lapCount, List<LaneDto> lanes) {
        this(event, heat, status, started, ended, lapCount, lanes, null);
    }

    public static HeatDto empty(int event, int heat, int laneCount) {
        List<LaneDto> lanes = IntStream.rangeClosed(1, laneCount)
                                       .mapToObj(number -> new LaneDto(number, 0, 0, LaneStatus.NotUsed, Penalty.None, 0, false))
                                       .toList();
        return new HeatDto(event, heat, HeatStatus.Open, null, null, 0, lanes);
    }
}
