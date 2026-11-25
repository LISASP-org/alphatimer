package org.lisasp.alphatimer.test.refinedmessages;

import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeOrigin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000"),
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
                                 RefinedTimeType.Normal, RefinedTimeOrigin.Primary)), listener.received);
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
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000"),
                new DroppedTimeMessage(message)), listener.received);
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
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000"),
                new DroppedTimeMessage(message)), listener.received);
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
                new UsedLanesMessage(TIMESTAMP, "TestWK", event, heat, "1100000000"),
                new DroppedTimeMessage(message)), listener.received);
    }

    private static final byte LAP_COUNT = 2;
    private static final byte EVENT = 3;
    private static final byte HEAT = 4;
    private static final UsedLanes LANES_1_AND_2 = new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false});

    private static DataHandlingMessage time(MessageType messageType, TimeType timeType, byte lane, TimeInfo timeInfo) {
        return new DataHandlingMessage(TIMESTAMP, "TestWK", "1", "2", messageType, KindOfTime.Finish, timeType, LANES_1_AND_2, LAP_COUNT, EVENT,
                                       HEAT, (byte) 1, RankInfo.Normal, lane, (byte) 2, 65_430, timeInfo, TimeMarker.Empty);
    }

    private static TimeMessage refined(RefinedMessageType messageType, RefinedTimeType timeType) {
        return new TimeMessage(TIMESTAMP, "TestWK", EVENT, HEAT, messageType, RefinedKindOfTime.Finish, (byte) 2, (byte) 2, LAP_COUNT, (byte) 1,
                               65_430, timeType, RefinedTimeOrigin.Primary);
    }

    @Test
    void theUsedLanesComeBeforeTheTimeOfTheSameMessage() {
        refiner.accept(time(MessageType.OnLineTime, TimeType.Empty, (byte) 2, TimeInfo.Normal));

        assertEquals(List.of(new UsedLanesMessage(TIMESTAMP, "TestWK", EVENT, HEAT, "1100000000"), refined(RefinedMessageType.Live, RefinedTimeType.Normal)),
                     listener.received);
    }

    @ParameterizedTest
    @CsvSource({"CurrentRaceResults, CurrentRaceResult", "PreviousRaceResults, PreviousRaceResult",
                "CurrentRaceResultsWithBackupTimes, CurrentRaceResult", "PreviousRaceResultsWithBackupTimes, PreviousRaceResult"})
    void aResultMessageKeepsItsMessageTypeAndCarriesNoUsedLanes(MessageType messageType, RefinedMessageType refinedMessageType) {
        refiner.accept(time(messageType, TimeType.Empty, (byte) 2, TimeInfo.Normal));

        assertEquals(List.of(refined(refinedMessageType, RefinedTimeType.Normal)), listener.received);
    }

    @ParameterizedTest
    @CsvSource({"CorrectedTime, CorrectedTime", "DeletedTime, DeletedTime"})
    void aCorrectedOrDeletedTimeIsRefinedWithItsTimeType(TimeType timeType, RefinedTimeType refinedTimeType) {
        refiner.accept(time(MessageType.CurrentRaceResults, timeType, (byte) 2, TimeInfo.Normal));

        assertEquals(List.of(refined(RefinedMessageType.CurrentRaceResult, refinedTimeType)), listener.received);
    }

    @ParameterizedTest
    @CsvSource({"Manual", "Edited", "UnknownAsterisk"})
    void aDeletionIsRefinedAsDeletionWhateverItsTimeInfo(TimeInfo timeInfo) {
        refiner.accept(time(MessageType.CurrentRaceResults, TimeType.DeletedTime, (byte) 2, timeInfo));

        assertEquals(List.of(refined(RefinedMessageType.CurrentRaceResult, RefinedTimeType.DeletedTime)), listener.received);
    }

    /** ARES manual 5.3.6 and 7: a manually generated time is a valid time ([V]M); the recordings mark it in the time type only. */
    @ParameterizedTest
    @CsvSource({"ManualTime, Normal", "ManualTime, Manual", "Empty, Manual"})
    void aManualTimeIsRefinedAsManualTime(TimeType timeType, TimeInfo timeInfo) {
        refiner.accept(time(MessageType.CurrentRaceResults, timeType, (byte) 2, timeInfo));

        assertEquals(List.of(refined(RefinedMessageType.CurrentRaceResult, RefinedTimeType.ManualTime)), listener.received);
    }

    /** ARES manual 7: a time edited from the keyboard is a valid time ([V]E). */
    @ParameterizedTest
    @CsvSource({"Empty", "CorrectedTime"})
    void anEditedTimeIsRefinedAsCorrectedTime(TimeType timeType) {
        refiner.accept(time(MessageType.CurrentRaceResults, timeType, (byte) 2, TimeInfo.Edited));

        assertEquals(List.of(refined(RefinedMessageType.CurrentRaceResult, RefinedTimeType.CorrectedTime)), listener.received);
    }

    @Test
    void aTimeWithoutLaneIsDropped() {
        DataHandlingMessage message = time(MessageType.CurrentRaceResults, TimeType.Empty, (byte) 0, TimeInfo.Normal);

        refiner.accept(message);

        assertEquals(List.of(new DroppedTimeMessage(message)), listener.received);
    }
}
