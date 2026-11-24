package org.lisasp.alphatimer.test.refinedmessages;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.LaneNumbering;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LaneNumberingTest {

    @Test
    void theUsedLanesOfAresAreLanesOneToTenNumberedFromOne() {
        UsedLanesMessage message = new UsedLanesMessage(LocalDateTime.of(2021, 6, 1, 10, 0), "TestWK", (short) 3, (byte) 4, "1100000001");

        assertThat(message.getLanes()).containsExactly(1, 2, 10);
        assertThat(message.getNumbering()).isEqualTo(LaneNumbering.OneBased);
    }

    @Test
    void malformedUsedLanesOfAresAreRejected() {
        assertThatThrownBy(() -> new UsedLanesMessage(LocalDateTime.of(2021, 6, 1, 10, 0), "TestWK", (short) 3, (byte) 4, "short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UsedLanesMessage(LocalDateTime.of(2021, 6, 1, 10, 0), "TestWK", (short) 3, (byte) 4, "11000000x0"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
