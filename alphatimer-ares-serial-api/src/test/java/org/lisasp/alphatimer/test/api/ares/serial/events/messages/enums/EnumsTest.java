package org.lisasp.alphatimer.test.api.ares.serial.events.messages.enums;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumsTest {
    @Test
    void kindOfTime() {
        Assertions.assertEquals('A', KindOfTime.Finish.getValue());
    }

    @Test
    void messageType() {
        Assertions.assertEquals(5, MessageType.PreviousRaceResults.getValue());
    }

    @Test
    void rankInfo() {
        Assertions.assertEquals('D', RankInfo.StartingBlock.getValue());
    }

    @Test
    void timeInfo() {
        Assertions.assertEquals('M', TimeInfo.Manual.getValue());
    }

    @Test
    void timeMarker() {
        Assertions.assertEquals("DNS", TimeMarker.DidNotStart.getValue());
        Assertions.assertEquals("DSQ", TimeMarker.Disqualified.getValue());
    }

    @Test
    void timeType() {
        assertEquals('I', TimeType.InsertedTime.getValue());
    }
}
