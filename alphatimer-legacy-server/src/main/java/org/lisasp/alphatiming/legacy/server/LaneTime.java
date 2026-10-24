package org.lisasp.alphatiming.legacy.server;

import org.lisasp.alphatiming.legacy.server.dto.LaneStatus;

import java.time.LocalDateTime;

/** One time of one lane, in the form the legacy (AlphaServer) endpoints need it. */
public record LaneTime(String competition,
                       int event,
                       int heatNumber,
                       int laneIndex,
                       long timeInMillis,
                       LaneStatus status,
                       LocalDateTime timestamp) implements Comparable<LaneTime> {

    @Override
    public int compareTo(LaneTime o) {
        if (o == null) {
            return 1;
        }
        if (!competition.equals(o.competition())) {
            return competition.compareTo(o.competition());
        }
        if (event != o.event()) {
            return event - o.event();
        }
        if (heatNumber != o.heatNumber()) {
            return heatNumber - o.heatNumber();
        }
        return timestamp.compareTo(o.timestamp());
    }
}
