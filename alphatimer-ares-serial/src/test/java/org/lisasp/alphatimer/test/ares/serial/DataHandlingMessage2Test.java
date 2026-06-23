package org.lisasp.alphatimer.test.ares.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnstructuredInputDroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage2;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageConverter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.lisasp.alphatimer.test.ares.serial.DataHandlingMessageTestData.bogus;
import static org.lisasp.alphatimer.test.ares.serial.DataHandlingMessageTestData.message2;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Corresponds to chapter 2
 */
class DataHandlingMessage2Test {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 21, 15, 52);

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
    void sendMessage2() {
        for (byte b : message2) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                new String(message2),
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Empty)), listener.received);
    }

    @Test
    void sendMessage2WithModifiedLap() {
        byte[] message2modified = Arrays.copyOf(message2, message2.length);
        message2modified[5] = 0x20;
        message2modified[6] = 0x31;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                new String(message2modified),
                (byte) 1,
                (byte) 1,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Empty)), listener.received);
    }

    @Test
    void sendMessage2AfterSomeBogusData() {
        for (byte b : bogus) {
            inputCollector.accept(b);
        }
        for (byte b : message2) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(
                new UnstructuredInputDroppedEvent(TIMESTAMP, "TestWK", bogus),
                new DataHandlingMessage2(
                        TIMESTAMP,
                        "TestWK",
                        new String(message2),
                        (byte) 1,
                        (byte) 0,
                        112853930,
                        TimeInfo.Normal,
                        TimeMarker.Empty)), listener.received);
    }
}
