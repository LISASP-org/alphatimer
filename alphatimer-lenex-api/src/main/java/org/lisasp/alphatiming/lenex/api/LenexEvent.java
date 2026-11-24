package org.lisasp.alphatiming.lenex.api;

import java.util.List;

/**
 * @param round  the round as written (e.g. {@code FIN}); empty if the file has none
 * @param course the pool of the event's session, else of the meet (e.g. {@code LCM}, {@code SCM}); empty if neither names one
 */
public record LenexEvent(int number, String round, String course, int distance, int relayCount, List<LenexHeat> heats) {

    public LenexEvent {
        heats = List.copyOf(heats);
    }
}
