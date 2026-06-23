package org.lisasp.alphatimer.test.ares.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.DataInputEventListener;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnstructuredInputDroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage1;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.RankInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageConverter;
import org.lisasp.basics.jre.date.DateTimeFacade;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lisasp.alphatimer.test.ares.serial.DataHandlingMessageTestData.*;

/**
 * Corresponds to chapter 2
 */
class DataHandlingMessage1Test {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 21, 14, 53);

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
    void sendMessage1() {
        for (byte b : message1) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage1(
                TIMESTAMP,
                "TestWK",
                new String(message1),
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
    void sendMessage1WithRank1() {
        byte[] message1modified = Arrays.copyOf(message1, message1.length);
        message1modified[18] = 0x31;

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
                (byte) 1,
                RankInfo.Normal)), listener.received);
    }

    @Test
    void sendMessage1AfterSomeBogusData() {
        for (byte b : bogus) {
            inputCollector.accept(b);
        }
        for (byte b : message1) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(
                new UnstructuredInputDroppedEvent(TIMESTAMP, "TestWK", bogus),
                new DataHandlingMessage1(
                        TIMESTAMP,
                        "TestWK",
                        new String(message1),
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
}
