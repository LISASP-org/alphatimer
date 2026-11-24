package org.lisasp.alphatiming.lenex;

import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.Round;

public final class LenexRounds {

    private LenexRounds() {
    }

    /** The round of an event as text of the refined messages; a missing round is {@code TIM}, the default of LENEX 3.0. */
    public static String consolidated(String lenex) {
        Round round = switch (lenex) {
            case "", "TIM" -> Round.TimedFinal;
            case "FHT" -> Round.FastestHeats;
            case "PRE" -> Round.PreliminaryHeat;
            case "QUA" -> Round.QuarterFinal;
            case "SEM" -> Round.SemiFinal;
            case "FIN" -> Round.Final;
            case "SOP" -> Round.SwimOffAfterPrelim;
            case "SOQ" -> Round.SwimOffAfterQuarterFinal;
            case "SOS" -> Round.SwimOffAfterSemiFinal;
            case "TIMETRIAL" -> Round.TimeTrial;
            default -> null;
        };
        return round == null ? lenex : round.name();
    }
}
