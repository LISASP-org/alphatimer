package org.lisasp.alphatimer.test.refinedmessages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DisqualifiedMessage;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedDisqualifiedMessage;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A disqualification arrives in the result of the heat as "DSQ" in place of the lane's time, like DNS and DNF. */
class DisqualifiedMessageTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 1, 10, 0);
    private static final short EVENT = 44;
    private static final byte HEAT = 1;
    private static final UsedLanes LANES_1_TO_4 = new UsedLanes(new boolean[]{true, true, true, true, false, false, false, false, false, false});

    private DataHandlingMessageRefiner refiner;
    private TestRefinedMessageListener listener;

    @BeforeEach
    void prepare() {
        listener = new TestRefinedMessageListener();

        refiner = new DataHandlingMessageRefiner();
        refiner.register(listener);
    }

    private static DataHandlingMessage disqualified(KindOfTime kindOfTime, byte lane, byte currentLap, int timeInMillis, TimeInfo timeInfo) {
        return new DataHandlingMessage(TIMESTAMP, "TestWK", "1", "2", MessageType.CurrentRaceResults, kindOfTime, TimeType.Empty, LANES_1_TO_4,
                                       (byte) 0, EVENT, HEAT, (byte) 0, RankInfo.Normal, lane, currentLap, timeInMillis, timeInfo,
                                       TimeMarker.Disqualified);
    }

    @Test
    void valid() {
        refiner.accept(disqualified(KindOfTime.Empty, (byte) 3, (byte) 0, 0, TimeInfo.Normal));

        assertEquals(List.of(new DisqualifiedMessage(TIMESTAMP, "TestWK", EVENT, HEAT, (byte) 3)), listener.received);
    }

    @Test
    void invalidKindOfTime() {
        DataHandlingMessage message = disqualified(KindOfTime.ReactionTimeAtStart, (byte) 3, (byte) 0, 0, TimeInfo.Normal);

        refiner.accept(message);

        assertEquals(List.of(new DroppedDisqualifiedMessage(message)), listener.received);
    }

    @Test
    void invalidLane() {
        DataHandlingMessage message = disqualified(KindOfTime.Empty, (byte) 0, (byte) 0, 0, TimeInfo.Normal);

        refiner.accept(message);

        assertEquals(List.of(new DroppedDisqualifiedMessage(message)), listener.received);
    }

    @Test
    void invalidCurrentLap() {
        DataHandlingMessage message = disqualified(KindOfTime.Empty, (byte) 3, (byte) 1, 0, TimeInfo.Normal);

        refiner.accept(message);

        assertEquals(List.of(new DroppedDisqualifiedMessage(message)), listener.received);
    }

    @Test
    void invalidTimeInMillis() {
        DataHandlingMessage message = disqualified(KindOfTime.Empty, (byte) 3, (byte) 0, 65_430, TimeInfo.Normal);

        refiner.accept(message);

        assertEquals(List.of(new DroppedDisqualifiedMessage(message)), listener.received);
    }

    @Test
    void invalidTimeInfo() {
        DataHandlingMessage message = disqualified(KindOfTime.Empty, (byte) 3, (byte) 0, 0, TimeInfo.Edited);

        refiner.accept(message);

        assertEquals(List.of(new DroppedDisqualifiedMessage(message)), listener.received);
    }
}
