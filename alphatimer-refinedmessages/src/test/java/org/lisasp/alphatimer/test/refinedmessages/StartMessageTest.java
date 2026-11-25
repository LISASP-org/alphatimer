package org.lisasp.alphatimer.test.refinedmessages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.refinedmessages.accepted.StartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedStartMessage;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StartMessageTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 1, 10, 0);

    private DataHandlingMessageRefiner refiner;
    private TestRefinedMessageListener listener;

    @BeforeEach
    void prepare() {
        listener = new TestRefinedMessageListener();

        refiner = new DataHandlingMessageRefiner();
        refiner.register(listener);
    }

    @Test
    void valid() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        refiner.accept(new DataHandlingMessage(
                TIMESTAMP, "TestWK",
                "1", "2",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Normal,
                (byte) 0,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000"),
                new StartMessage(
                        TIMESTAMP,
                        "TestWK",
                        event,
                        heat,
                        RefinedMessageType.Live,
                        lapCount,
                        (byte) 0,
                        0,
                        RefinedTimeType.Normal)), listener.received);
    }

    @Test
    void invalidTimeType() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        refiner.accept(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.DeletedTime,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Normal,
                (byte) 0,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000"),
                new DroppedStartMessage(new DataHandlingMessage(
                        TIMESTAMP,
                        "TestWK",
                        "1", "2",
                        MessageType.OnLineTime,
                        KindOfTime.Start,
                        TimeType.DeletedTime,
                        new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                        lapCount,
                        event,
                        heat,
                        (byte) 0,
                        RankInfo.Normal,
                        (byte) 0,
                        (byte) 0,
                        0,
                        TimeInfo.Normal,
                        TimeMarker.Empty))), listener.received);
    }

    @Test
    void invalidRank() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        refiner.accept(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 1,
                RankInfo.Normal,
                (byte) 0,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000"),
                new DroppedStartMessage(new DataHandlingMessage(
                        TIMESTAMP,
                        "TestWK",
                        "1", "2",
                        MessageType.OnLineTime,
                        KindOfTime.Start,
                        TimeType.Empty,
                        new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                        lapCount,
                        event,
                        heat,
                        (byte) 1,
                        RankInfo.Normal,
                        (byte) 0,
                        (byte) 0,
                        0,
                        TimeInfo.Normal,
                        TimeMarker.Empty))), listener.received);
    }

    @Test
    void invalidRankInfo() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        refiner.accept(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.UnknownValueD,
                (byte) 0,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000"),
                new DroppedStartMessage(new DataHandlingMessage(
                        TIMESTAMP,
                        "TestWK",
                        "1", "2",
                        MessageType.OnLineTime,
                        KindOfTime.Start,
                        TimeType.Empty,
                        new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                        lapCount,
                        event,
                        heat,
                        (byte) 0,
                        RankInfo.UnknownValueD,
                        (byte) 0,
                        (byte) 0,
                        0,
                        TimeInfo.Normal,
                        TimeMarker.Empty))), listener.received);
    }

    @Test
    void invalidCurrentLap() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        refiner.accept(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Normal,
                (byte) 0,
                (byte) 1,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000"),
                new DroppedStartMessage(new DataHandlingMessage(
                        TIMESTAMP,
                        "TestWK",
                        "1", "2",
                        MessageType.OnLineTime,
                        KindOfTime.Start,
                        TimeType.Empty,
                        new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                        lapCount,
                        event,
                        heat,
                        (byte) 0,
                        RankInfo.Normal,
                        (byte) 0,
                        (byte) 1,
                        0,
                        TimeInfo.Normal,
                        TimeMarker.Empty))), listener.received);
    }

    @Test
    void invalidTimeMarker() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        refiner.accept(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Normal,
                (byte) 0,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.DidNotStart));

        assertEquals(List.of(
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000"),
                new DroppedStartMessage(new DataHandlingMessage(
                        TIMESTAMP,
                        "TestWK",
                        "1", "2",
                        MessageType.OnLineTime,
                        KindOfTime.Start,
                        TimeType.Empty,
                        new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                        lapCount,
                        event,
                        heat,
                        (byte) 0,
                        RankInfo.Normal,
                        (byte) 0,
                        (byte) 0,
                        0,
                        TimeInfo.Normal,
                        TimeMarker.DidNotStart))), listener.received);
    }

    private static final byte LAP_COUNT = 2;
    private static final byte EVENT = 3;
    private static final byte HEAT = 4;
    private static final UsedLanes LANES_1_AND_2 = new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false});

    private static DataHandlingMessage start(MessageType messageType, TimeType timeType) {
        return new DataHandlingMessage(TIMESTAMP, "TestWK", "1", "2", messageType, KindOfTime.Start, timeType, LANES_1_AND_2, LAP_COUNT, EVENT, HEAT,
                                       (byte) 0, RankInfo.Normal, (byte) 1, (byte) 0, 36_767_030, TimeInfo.Normal, TimeMarker.Empty);
    }

    private static StartMessage refined(RefinedMessageType messageType, RefinedTimeType timeType) {
        return new StartMessage(TIMESTAMP, "TestWK", EVENT, HEAT, messageType, LAP_COUNT, (byte) 1, 36_767_030, timeType);
    }

    @ParameterizedTest
    @CsvSource({"CurrentRaceResults, CurrentRaceResult", "PreviousRaceResults, PreviousRaceResult",
                "CurrentRaceResultsWithBackupTimes, CurrentRaceResult", "PreviousRaceResultsWithBackupTimes, PreviousRaceResult"})
    void aResultMessageKeepsItsMessageTypeAndCarriesNoUsedLanes(MessageType messageType, RefinedMessageType refinedMessageType) {
        refiner.accept(start(messageType, TimeType.Empty));

        assertEquals(List.of(refined(refinedMessageType, RefinedTimeType.Normal)), listener.received);
    }

    @ParameterizedTest
    @CsvSource({"CorrectedTime, CorrectedTime", "ManualTime, ManualTime"})
    void aCorrectedOrManualStartIsRefinedWithItsTimeType(TimeType timeType, RefinedTimeType refinedTimeType) {
        refiner.accept(start(MessageType.CurrentRaceResults, timeType));

        assertEquals(List.of(refined(RefinedMessageType.CurrentRaceResult, refinedTimeType)), listener.received);
    }
}
