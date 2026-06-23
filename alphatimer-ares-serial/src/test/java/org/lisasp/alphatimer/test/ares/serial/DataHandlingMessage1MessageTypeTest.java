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
class DataHandlingMessage1MessageTypeTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 21, 14, 52);

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
    void sendMessage1ReadyToStart() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[3] = 0x30;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.ReadyToStart,
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
    void sendMessage1OfficialEnd() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[3] = 0x31;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.OfficialEnd,
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
    void sendMessage1OnLineTime() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[3] = 0x32;

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
    void sendMessage1CurrentRaceResults() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[3] = 0x33;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.CurrentRaceResults,
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
    void sendMessage1CurrentRaceResultsWithBackupTimes() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[3] = 0x34;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.CurrentRaceResultsWithBackupTimes,
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
    void sendMessage1PreviousRaceResults() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[3] = 0x35;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.PreviousRaceResults,
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
    void sendMessage1PreviousRaceResultsWithBackupTimes() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[3] = 0x36;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.PreviousRaceResultsWithBackupTimes,
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
    void sendMessage1_7() {
        byte[] message1modified = Arrays.copyOf(DataHandlingMessageTestData.message1,
                                                DataHandlingMessageTestData.message1.length);
        message1modified[3] = 0x37;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1modified),
                MessageType.UnknownValue7,
                KindOfTime.Start,
                TimeType.Empty,
                createUsedLanes(),
                (byte) 2,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal)), listener.received);
    }
}
