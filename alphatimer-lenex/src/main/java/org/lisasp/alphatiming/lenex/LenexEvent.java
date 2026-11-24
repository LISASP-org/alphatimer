package org.lisasp.alphatiming.lenex;

import java.util.List;

/**
 * @param course the pool of the event's session, else of the meet (e.g. {@code LCM}, {@code SCM}); empty if neither names one
 */
public record LenexEvent(int number, String course, int distance, int relayCount, List<LenexHeat> heats) {

    public LenexEvent {
        heats = List.copyOf(heats);
    }
}
