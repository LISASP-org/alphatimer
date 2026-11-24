package org.lisasp.alphatiming.lenex.api;

import java.util.List;
import java.util.OptionalInt;

/**
 * @param laneMin the first lane of the pool ({@code POOL lanemin} of the meet); empty if the file does not say
 */
public record LenexMeet(OptionalInt laneMin, List<LenexEvent> events) {

    public LenexMeet {
        events = List.copyOf(events);
    }
}
