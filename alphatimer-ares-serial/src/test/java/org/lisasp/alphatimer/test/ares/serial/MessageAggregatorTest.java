package org.lisasp.alphatimer.test.ares.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageAggregator;
import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageListener;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnstructuredInputDroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage1;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage2;
import org.lisasp.alphatimer.api.ares.serial.events.messages.Ping;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.alphatimer.ares.serial.MessageAggregator;

import java.time.LocalDateTime;
import java.util.List;

import static org.lisasp.alphatimer.test.ares.serial.DataHandlingMessageTestData.bogus;
import static org.lisasp.alphatimer.test.ares.serial.DataHandlingMessageTestData.createUsedLanes;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Corresponds to chapter 2
 */
class MessageAggregatorTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 1, 10, 0);

    private DataHandlingMessageAggregator aggregator;
    private TestDataHandlingMessageListener listener;

    @BeforeEach
    void prepare() {
        DateTimeFacade dateTimeFacade = new DateTimeFacade() {
            @Override
            public LocalDateTime now() {
                return TIMESTAMP;
            }
        };

        listener = new TestDataHandlingMessageListener();
        aggregator = new MessageAggregator();
        aggregator.register(listener);
    }

    @AfterEach
    void cleanUp() {
        aggregator = null;
        listener = null;
    }

    @Test
    void register() {
        aggregator.accept(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                "1",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal));
        aggregator.accept(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                "2",
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1",
                "2",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal,
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Empty)), listener.received);
    }

    @Test
    void sendMessage1And2() {
        aggregator.accept(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                "1",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal));
        aggregator.accept(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                "2",
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1",
                "2",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal,
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Empty)), listener.received);
    }

    @Test
    void sendMessage1AndPing() {
        aggregator.accept(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                "1",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal));
        aggregator.accept(new Ping(TIMESTAMP, "TestWK", new byte[]{0x12, 0x39}));

        assertEquals(List.of(), listener.received);
    }

    @Test
    void sendMessage2AndPing() {
        aggregator.accept(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                "2",
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Empty));
        aggregator.accept(new Ping(TIMESTAMP, "TestWK", new byte[]{0x12, 0x39}));

        assertEquals(List.of(), listener.received);
    }

    @Test
    void sendMessage1AndPingAndMessage2() {
        aggregator.accept(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                "1",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal));
        aggregator.accept(new Ping(TIMESTAMP, "TestWK", new byte[]{0x12, 0x39}));
        aggregator.accept(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                "2",
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1",
                "2",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal,
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Empty)), listener.received);
    }

    @Test
    void sendMessage1AndPingAndBogusAndMessage2() {
        aggregator.accept(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                "1",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal));
        aggregator.accept(new Ping(TIMESTAMP, "TestWK", new byte[]{0x12, 0x39}));
        aggregator.accept(new UnstructuredInputDroppedEvent(TIMESTAMP, "TestWK", bogus));
        aggregator.accept(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                "2",
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(), listener.received);
    }

    @Test
    void convenienceConstructorTest() {
        listener = new TestDataHandlingMessageListener();
        aggregator = new MessageAggregator();
        aggregator.register(listener);

        aggregator.accept(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                "1",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal));
        aggregator.accept(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                "2",
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Empty));

        assertEquals(List.of(new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1",
                "2",
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal,
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Empty)), listener.received);
    }
}
