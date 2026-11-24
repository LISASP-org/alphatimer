package org.lisasp.alphatiming.lenex;

import java.util.List;

public record LenexMeet(List<LenexEvent> events) {

    public LenexMeet {
        events = List.copyOf(events);
    }
}
