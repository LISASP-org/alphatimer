package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.Round;

import java.util.Arrays;

public final class RoundTexts {

    private RoundTexts() {
    }

    /** A known round in the language of the user, any other round as the source wrote it; empty if the heat has none. */
    public static String label(String round) {
        return Arrays.stream(Round.values())
                     .filter(r -> r.name().equals(round))
                     .findFirst()
                     .map(r -> I18n.get("round." + r.name()))
                     .orElse(round);
    }
}
