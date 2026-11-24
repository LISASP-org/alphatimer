package org.lisasp.alphatimer.test.refinedmessages;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RoundTest {

    @Test
    void aresSendsNoRound() {
        TestRefinedMessageListener listener = new TestRefinedMessageListener();
        DataHandlingMessageRefiner refiner = new DataHandlingMessageRefiner();
        refiner.register(listener);

        refiner.accept(new DataHandlingMessage(LocalDateTime.of(2021, 6, 1, 10, 0), "TestWK", "1", "2", MessageType.OnLineTime,
                                               KindOfTime.Finish, TimeType.Empty,
                                               new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                                               (byte) 2, (short) 3, (byte) 4, (byte) 0, RankInfo.Normal, (byte) 2, (byte) 1, 61230,
                                               TimeInfo.Normal, TimeMarker.Empty));

        assertThat(listener.received).isNotEmpty().extracting(RefinedMessage::getRound).containsOnly("");
    }
}
