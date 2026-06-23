package org.lisasp.alphatimer.test.ares.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.DataInputEventListener;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage1;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.RankInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageConverter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lisasp.alphatimer.test.ares.serial.DataHandlingMessageTestData.createUsedLanes;

/**
 * Corresponds to chapter 2
 */
class DataHandlingMessage1TimeTypeTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 21, 15, 53);

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
    void sendMessage1CorrectedTime() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[5] = 0x43;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.CorrectedTime,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1DeletedTime() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[5] = 0x45;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.DeletedTime,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1FalseStartAtRelay() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[5] = 0x46;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.FalseStartAtRelay,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1InsertedTime() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[5] = 0x49;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.InsertedTime,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1ManualTime() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[5] = 0x4D;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.ManualTime,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1TimeIsMissing() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[5] = 0x4E;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.TimeIsMissing,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1OneOrTwoPushButtonTimesAreMissing() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[5] = 0x54;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.OneOrTwoPushButtonTimesAreMissing,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1PlatformTimeAfterTouchpadTime() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[5] = 0x2B;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.PlatformTimeAfterTouchpadTime,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1PlatformTimeBeforeTouchpadTime() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[5] = 0x2D;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.PlatformTimeBeforeTouchpadTime,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1Empty() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[5] = 0x20;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.Empty,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1_1() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[5] = 0x31;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Start,
                TimeType.UnknownValue1,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }
}
