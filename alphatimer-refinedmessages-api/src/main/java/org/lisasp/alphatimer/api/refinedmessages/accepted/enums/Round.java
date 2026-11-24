package org.lisasp.alphatimer.api.refinedmessages.accepted.enums;

/**
 * The rounds known to AlphaTiming. A refined message carries its round as text: the name of one of these, a value of the
 * source that matches none of them, a round number as a number string, or empty when the source sends no round.
 */
public enum Round {
    TimedFinal,
    FastestHeats,
    PreliminaryHeat,
    QuarterFinal,
    SemiFinal,
    Final,
    SwimOffAfterPrelim,
    SwimOffAfterQuarterFinal,
    SwimOffAfterSemiFinal,
    TimeTrial
}
