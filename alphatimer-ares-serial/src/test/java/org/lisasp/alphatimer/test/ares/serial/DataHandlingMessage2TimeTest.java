package org.lisasp.alphatimer.test.ares.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage2;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageConverter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.lisasp.alphatimer.test.ares.serial.DataHandlingMessageTestData.message2;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Corresponds to chapter 2
 */
class DataHandlingMessage2TimeTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 21, 14, 51);

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
    void sendMessage2Empty() {
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
    void sendMessage2WithMinus() {
        byte[] message2modified = Arrays.copyOf(message2,
                                                message2.length + 1);
        System.arraycopy(message2,
                         8,
                         message2modified,
                         9,
                         message2.length - 8);
        message2modified[8] = 0x2D;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                new String(message2modified),
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Normal,
                TimeMarker.Minus)), listener.received);
    }

    @Test
    void sendMessage2WithBackup() {
        byte[] message2modified = Arrays.copyOf(message2,
                                                message2.length);
        message2modified[19] = 0x42;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                new String(message2modified),
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Backup,
                TimeMarker.Empty)), listener.received);
    }

    @Test
    void sendMessage2Edited() {
        byte[] message2modified = Arrays.copyOf(message2,
                                                message2.length);
        message2modified[19] = 0x45;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                new String(message2modified),
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Edited,
                TimeMarker.Empty)), listener.received);
    }

    @Test
    void sendMessage2Asterisk() {
        byte[] message2modified = Arrays.copyOf(message2,
                                                message2.length);
        message2modified[19] = 0x2A;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                new String(message2modified),
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.UnknownAsterisk,
                TimeMarker.Empty)), listener.received);
    }

    @Test
    void sendMessage2Manual() {
        byte[] message2modified = Arrays.copyOf(message2,
                                                message2.length);
        message2modified[19] = 0x4D;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                new String(message2modified),
                (byte) 1,
                (byte) 0,
                112853930,
                TimeInfo.Manual,
                TimeMarker.Empty)), listener.received);
    }

    @Test
    void sendMessage2WithPlusAndTimePlaceholder() {
        byte[] message2modified = Arrays.copyOf(message2,
                                                message2.length);
        message2modified[8] = 0x20;
        message2modified[9] = 0x20;
        message2modified[10] = 0x20;
        message2modified[11] = 0x20;
        message2modified[12] = 0x2B;
        message2modified[13] = 0x20;
        message2modified[14] = 0x2D;
        message2modified[15] = 0x2E;
        message2modified[16] = 0x2D;
        message2modified[17] = 0x2D;
        message2modified[18] = 0x20;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                new String(message2modified),
                (byte) 1,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Plus)), listener.received);
    }

    @Test
    void sendMessage2WithTimePlaceholder() {
        byte[] message2modified = Arrays.copyOf(message2,
                                                message2.length);
        message2modified[8] = 0x20;
        message2modified[9] = 0x20;
        message2modified[10] = 0x20;
        message2modified[11] = 0x20;
        message2modified[12] = 0x20;
        message2modified[13] = 0x20;
        message2modified[14] = 0x2D;
        message2modified[15] = 0x2E;
        message2modified[16] = 0x2D;
        message2modified[17] = 0x2D;
        message2modified[18] = 0x20;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                new String(message2modified),
                (byte) 1,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty)), listener.received);
    }

    @Test
    void sendMessage2WithTimeDNS() {
        byte[] message2modified = Arrays.copyOf(message2,
                                                message2.length);
        message2modified[8] = 0x20;
        message2modified[9] = 0x20;
        message2modified[10] = 0x20;
        message2modified[11] = 0x20;
        message2modified[12] = 0x20;
        message2modified[13] = 0x20;
        message2modified[14] = 0x20;
        message2modified[15] = 0x20;
        message2modified[16] = 0x44;
        message2modified[17] = 0x4E;
        message2modified[18] = 0x53;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                new String(message2modified),
                (byte) 1,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.DidNotStart)), listener.received);
    }

    @Test
    void sendMessage2WithTimeDNF() {
        byte[] message2modified = Arrays.copyOf(message2,
                                                message2.length);
        message2modified[8] = 0x20;
        message2modified[9] = 0x20;
        message2modified[10] = 0x20;
        message2modified[11] = 0x20;
        message2modified[12] = 0x20;
        message2modified[13] = 0x20;
        message2modified[14] = 0x20;
        message2modified[15] = 0x20;
        message2modified[16] = 0x44;
        message2modified[17] = 0x4E;
        message2modified[18] = 0x46;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2(
                TIMESTAMP,
                "TestWK",
                new String(message2modified),
                (byte) 1,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.DidNotFinish)), listener.received);
    }
}
