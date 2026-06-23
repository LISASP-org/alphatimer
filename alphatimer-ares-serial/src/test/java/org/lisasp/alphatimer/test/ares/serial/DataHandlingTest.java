package org.lisasp.alphatimer.test.ares.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnstructuredInputDroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage1;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage2;
import org.lisasp.alphatimer.api.ares.serial.events.messages.Ping;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageConverter;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Corresponds to chapter 2
 */
class DataHandlingTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 21, 14, 54);

    private InputCollector inputCollector;
    private TestDataInputEventListener listener;

    @BeforeEach
    void prepare() {
        DateTimeFacade dateTimeFacade = new DateTimeFacade() {
            @Override
            public LocalDateTime now() {
                return TIMESTAMP;
            }
        };

        listener = new TestDataInputEventListener();

        MessageConverter messageConverter = new MessageConverter();
        messageConverter.register(listener);

        inputCollector = new InputCollector("TestWK", dateTimeFacade);
        inputCollector.register(messageConverter);
    }

    @AfterEach
    void cleanUp() {
        inputCollector = null;
        listener = null;
    }

    @Test
    void sendMessage1And2() {
        for (byte b : DataHandlingMessageTestData.message1) {
            inputCollector.accept(b);
        }
        for (byte b : DataHandlingMessageTestData.message2) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(
                new DataHandlingMessage1(
                        TIMESTAMP,
                        "TestWK",
                        new String(DataHandlingMessageTestData.message1),
                        MessageType.OnLineTime,
                        KindOfTime.Start,
                        TimeType.Empty,
                        DataHandlingMessageTestData.createUsedLanes(),
                        (byte) 2,
                        (short) 1,
                        (byte) 1,
                        (byte) 0,
                        RankInfo.Normal),
                new DataHandlingMessage2(
                        TIMESTAMP,
                        "TestWK",
                        new String(DataHandlingMessageTestData.message2),
                        (byte) 1,
                        (byte) 0,
                        112853930,
                        TimeInfo.Normal,
                        TimeMarker.Empty)), listener.received);
    }

    @Test
    void sendMessage1AndPing() {
        for (byte b : DataHandlingMessageTestData.message1) {
            inputCollector.accept(b);
        }
        for (byte b : DataHandlingMessageTestData.ping) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(
                new DataHandlingMessage1(
                        TIMESTAMP,
                        "TestWK",
                        new String(DataHandlingMessageTestData.message1),
                        MessageType.OnLineTime,
                        KindOfTime.Start,
                        TimeType.Empty,
                        DataHandlingMessageTestData.createUsedLanes(),
                        (byte) 2,
                        (short) 1,
                        (byte) 1,
                        (byte) 0,
                        RankInfo.Normal),
                new Ping(TIMESTAMP, "TestWK", new byte[]{0x54, 0x50})), listener.received);
    }

    @Test
    void sendMessage2AndPing() {
        for (byte b : DataHandlingMessageTestData.message2) {
            inputCollector.accept(b);
        }
        for (byte b : DataHandlingMessageTestData.ping) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(
                new DataHandlingMessage2(
                        TIMESTAMP,
                        "TestWK",
                        new String(DataHandlingMessageTestData.message2),
                        (byte) 1,
                        (byte) 0,
                        112853930,
                        TimeInfo.Normal,
                        TimeMarker.Empty),
                new Ping(TIMESTAMP, "TestWK", new byte[]{0x54, 0x50})), listener.received);
    }

    @Test
    void sendMessage1AndPingAndMessage2() {
        for (byte b : DataHandlingMessageTestData.message1) {
            inputCollector.accept(b);
        }
        for (byte b : DataHandlingMessageTestData.ping) {
            inputCollector.accept(b);
        }
        for (byte b : DataHandlingMessageTestData.message2) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(
                new DataHandlingMessage1(
                        TIMESTAMP,
                        "TestWK",
                        new String(DataHandlingMessageTestData.message1),
                        MessageType.OnLineTime,
                        KindOfTime.Start,
                        TimeType.Empty,
                        DataHandlingMessageTestData.createUsedLanes(),
                        (byte) 2,
                        (short) 1,
                        (byte) 1,
                        (byte) 0,
                        RankInfo.Normal),
                new Ping(TIMESTAMP, "TestWK", new byte[]{0x54, 0x50}),
                new DataHandlingMessage2(
                        TIMESTAMP,
                        "TestWK",
                        new String(DataHandlingMessageTestData.message2),
                        (byte) 1,
                        (byte) 0,
                        112853930,
                        TimeInfo.Normal,
                        TimeMarker.Empty)), listener.received);
    }

    @Test
    void sendMessage1AndPingAndBogusAndMessage2() {
        for (byte b : DataHandlingMessageTestData.message1) {
            inputCollector.accept(b);
        }
        for (byte b : DataHandlingMessageTestData.ping) {
            inputCollector.accept(b);
        }
        for (byte b : DataHandlingMessageTestData.bogus) {
            inputCollector.accept(b);
        }
        for (byte b : DataHandlingMessageTestData.message2) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(
                new DataHandlingMessage1(
                        TIMESTAMP,
                        "TestWK",
                        new String(DataHandlingMessageTestData.message1),
                        MessageType.OnLineTime,
                        KindOfTime.Start,
                        TimeType.Empty,
                        DataHandlingMessageTestData.createUsedLanes(),
                        (byte) 2,
                        (short) 1,
                        (byte) 1,
                        (byte) 0,
                        RankInfo.Normal),
                new Ping(TIMESTAMP, "TestWK", new byte[]{0x54, 0x50}),
                new UnstructuredInputDroppedEvent(TIMESTAMP, "TestWK", DataHandlingMessageTestData.bogus),
                new DataHandlingMessage2(
                        TIMESTAMP,
                        "TestWK",
                        new String(DataHandlingMessageTestData.message2),
                        (byte) 1,
                        (byte) 0,
                        112853930,
                        TimeInfo.Normal,
                        TimeMarker.Empty)), listener.received);
    }
}
