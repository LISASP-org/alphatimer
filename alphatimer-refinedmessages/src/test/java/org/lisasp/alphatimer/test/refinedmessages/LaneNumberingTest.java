package org.lisasp.alphatimer.test.refinedmessages;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.LaneNumbering;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LaneNumberingTest {

    @Test
    void theUsedLanesOfAresAreLanesOneToTenNumberedFromOne() {
        TestRefinedMessageListener listener = new TestRefinedMessageListener();
        DataHandlingMessageRefiner refiner = new DataHandlingMessageRefiner();
        refiner.register(listener);

        refiner.accept(new DataHandlingMessage(LocalDateTime.of(2021, 6, 1, 10, 0), "TestWK", "1", "2", MessageType.ReadyToStart, KindOfTime.Empty,
                                               TimeType.Empty,
                                               new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, true}),
                                               (byte) 2, (short) 3, (byte) 4, (byte) 0, RankInfo.Normal, (byte) 0, (byte) 0, 0, TimeInfo.Normal,
                                               TimeMarker.Empty));

        UsedLanesMessage message = listener.received.stream()
                                                    .filter(UsedLanesMessage.class::isInstance)
                                                    .map(UsedLanesMessage.class::cast)
                                                    .findFirst()
                                                    .orElseThrow();
        assertThat(message.getLanes()).containsExactly(1, 2, 10);
        assertThat(message.getNumbering()).isEqualTo(LaneNumbering.OneBased);
    }
}
