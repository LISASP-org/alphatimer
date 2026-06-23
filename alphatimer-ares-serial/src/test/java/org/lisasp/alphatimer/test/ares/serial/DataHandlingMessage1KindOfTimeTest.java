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

/**
 * Corresponds to chapter 2
 */
class DataHandlingMessage1KindOfTimeTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 21, 14, 55);

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
    void sendMessage1SplitTime() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[4] = 0x49;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.SplitTime,
                TimeType.Empty,
                DataHandlingMessageTestData.createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1Finish() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[4] = 0x41;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Finish,
                TimeType.Empty,
                DataHandlingMessageTestData.createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1TakeOverTime() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[4] = 0x44;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.TakeOverTime,
                TimeType.Empty,
                DataHandlingMessageTestData.createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1ReactionTimeAtStart() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[4] = 0x52;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.ReactionTimeAtStart,
                TimeType.Empty,
                DataHandlingMessageTestData.createUsedLanes(),
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
        message1modified[4] = 0x20;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.Empty,
                TimeType.Empty,
                DataHandlingMessageTestData.createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1_B() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[4] = 0x42;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OnLineTime,
                KindOfTime.UnknownValueB,
                TimeType.Empty,
                DataHandlingMessageTestData.createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }
}
