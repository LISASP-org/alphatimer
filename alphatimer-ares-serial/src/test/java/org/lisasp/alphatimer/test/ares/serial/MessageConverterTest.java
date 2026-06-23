package org.lisasp.alphatimer.test.ares.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.BytesInputEvent;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage1;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage2;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.alphatimer.ares.serial.MessageConverter;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Corresponds to chapter 2
 */
class MessageConverterTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 1, 10, 0);

    public static final DataHandlingMessage1 TestDataHandlingMessage1 = new DataHandlingMessage1(
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
            RankInfo.Normal);
    public static final DataHandlingMessage2 TestDataHandlingMessage2 = new DataHandlingMessage2(
            TIMESTAMP,
            "TestWK",
            new String(DataHandlingMessageTestData.message2),
            (byte) 1,
            (byte) 0,
            112853930,
            TimeInfo.Normal,
            TimeMarker.Empty);

    private MessageConverter messageConverter;
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
        messageConverter = new MessageConverter();
        messageConverter.register(listener);
    }

    @AfterEach
    void cleanUp() {
        messageConverter = null;
        listener = null;
    }

    @Test
    void sendMessage1And2() {
        messageConverter.accept(new BytesInputEvent(TIMESTAMP, "TestWK", DataHandlingMessageTestData.message1));
        messageConverter.accept(new BytesInputEvent(TIMESTAMP, "TestWK", DataHandlingMessageTestData.message2));

        assertEquals(List.of(TestDataHandlingMessage1, TestDataHandlingMessage2), listener.received);
    }

    @Test
    void sendMessage1AndPing() {
        messageConverter.accept(new BytesInputEvent(TIMESTAMP, "TestWK", DataHandlingMessageTestData.message1));

        assertEquals(List.of(TestDataHandlingMessage1), listener.received);
    }
}
