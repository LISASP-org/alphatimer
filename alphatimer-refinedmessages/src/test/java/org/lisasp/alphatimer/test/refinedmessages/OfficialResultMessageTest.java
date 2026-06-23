package org.lisasp.alphatimer.test.refinedmessages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessageListener;
import org.lisasp.alphatimer.api.refinedmessages.accepted.OfficialEndMessage;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedOfficialEndMessage;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfficialResultMessageTest {

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
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.Empty,
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

        assertEquals(List.of(new OfficialEndMessage(TIMESTAMP, "TestWK", event, heat, lapCount)), listener.received);
    }

    @Test
    void invalidKindOfTime() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        refiner.accept(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.SplitTime,
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

        assertEquals(List.of(new DroppedOfficialEndMessage(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.SplitTime,
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
                TimeMarker.Empty))), listener.received);
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
                MessageType.OfficialEnd,
                KindOfTime.Empty,
                TimeType.CorrectedTime,
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

        assertEquals(List.of(new DroppedOfficialEndMessage(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.Empty,
                TimeType.CorrectedTime,
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
                MessageType.OfficialEnd,
                KindOfTime.Empty,
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

        assertEquals(List.of(new DroppedOfficialEndMessage(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.Empty,
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
                MessageType.OfficialEnd,
                KindOfTime.Empty,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Disqualified,
                (byte) 0,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(new DroppedOfficialEndMessage(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.Empty,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Disqualified,
                (byte) 0,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty))), listener.received);
    }

    @Test
    void invalidLane() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        refiner.accept(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.Empty,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Normal,
                (byte) 1,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(new DroppedOfficialEndMessage(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.Empty,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Normal,
                (byte) 1,
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
                MessageType.OfficialEnd,
                KindOfTime.Empty,
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

        assertEquals(List.of(new DroppedOfficialEndMessage(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.Empty,
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
    void invalidTimeInMillis() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        refiner.accept(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.Empty,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Normal,
                (byte) 0,
                (byte) 1,
                12345,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(new DroppedOfficialEndMessage(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.Empty,
                TimeType.Empty,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                event,
                heat,
                (byte) 0,
                RankInfo.Normal,
                (byte) 0,
                (byte) 1,
                12345,
                TimeInfo.Normal,
                TimeMarker.Empty))), listener.received);
    }

    @Test
    void invalidTimeInfo() {
        final byte lapCount = 2;
        final byte event = 3;
        final byte heat = 4;

        refiner.accept(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.Empty,
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
                TimeInfo.Backup,
                TimeMarker.Empty));

        assertEquals(List.of(new DroppedOfficialEndMessage(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.Empty,
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
                TimeInfo.Backup,
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
                MessageType.OfficialEnd,
                KindOfTime.Empty,
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

        assertEquals(List.of(new DroppedOfficialEndMessage(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                MessageType.OfficialEnd,
                KindOfTime.Empty,
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
}
