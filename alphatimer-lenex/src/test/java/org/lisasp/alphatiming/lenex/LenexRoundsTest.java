package org.lisasp.alphatiming.lenex;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LenexRoundsTest {

    @ParameterizedTest
    @CsvSource(value = {
            "TIM, TimedFinal", "'', TimedFinal", "FHT, FastestHeats", "PRE, PreliminaryHeat", "QUA, QuarterFinal",
            "SEM, SemiFinal", "FIN, Final", "SOP, SwimOffAfterPrelim", "SOQ, SwimOffAfterQuarterFinal",
            "SOS, SwimOffAfterSemiFinal", "TIMETRIAL, TimeTrial", "EXTRAHEATS, EXTRAHEATS"
    })
    void consolidatesTheKnownRoundsAndKeepsOthers(String lenex, String round) {
        assertThat(LenexRounds.consolidated(lenex)).isEqualTo(round);
    }
}
