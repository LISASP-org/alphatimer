package org.lisasp.alphatimer.test.refinedmessages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedTimeMessage;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeMessageTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 1, 10, 0);

    private DataHandlingMessageRefiner refiner;
    private TestRefinedMessageListener listener;

    @BeforeEach
    void prepare() {
        listener = new TestRefinedMessageListener();

        refiner = new DataHandlingMessageRefiner();
        refiner.register(listener);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SplitTime", "Finish"})
    void valid(String kindOfTimeString) {
        KindOfTime kindOfTime = KindOfTime.valueOf(kindOfTimeString);
        RefinedKindOfTime refinedKindOfTime = kindOfTime == KindOfTime.SplitTime ? RefinedKindOfTime.SplitTime : RefinedKindOfTime.Finish;
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        refiner.accept(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OnLineTime,
                kindOfTime,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Normal,
                (byte) 2,
                (byte) 1,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(
                new TimeMessage(TIMESTAMP,
                                 "TestWK",
                                 event,
                                 heat,
                                 RefinedMessageType.Live,
                                 refinedKindOfTime,
                                 (byte) 2,
                                 (byte) 1,
                                 lapCount,
                                 (byte) 0,
                                 0,
                                 RefinedTimeType.Normal),
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000")), listener.received);
    }

    @Test
    void invalidTimeType() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        DataHandlingMessage message = new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OnLineTime,
                KindOfTime.SplitTime,
                TimeType.PlatformTimeAfterTouchpadTime,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Normal,
                (byte) 2,
                (byte) 1,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty);

        refiner.accept(message);

        assertEquals(List.of(
                new DroppedTimeMessage(message),
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000")), listener.received);
    }

    @Test
    void invalidRankInfo() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;
        DataHandlingMessage message = new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OnLineTime,
                KindOfTime.SplitTime,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Disqualified,
                (byte) 2,
                (byte) 1,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty);

        refiner.accept(message);

        assertEquals(List.of(
                new DroppedTimeMessage(message),
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000")), listener.received);
    }

    @Test
    void invalidTimeMarker() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        DataHandlingMessage message = new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OnLineTime,
                KindOfTime.SplitTime,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Normal,
                (byte) 2,
                (byte) 1,
                0,
                TimeInfo.Normal,
                TimeMarker.Plus);

        refiner.accept(message);

        assertEquals(List.of(
                new DroppedTimeMessage(message),
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000")), listener.received);
    }
}
