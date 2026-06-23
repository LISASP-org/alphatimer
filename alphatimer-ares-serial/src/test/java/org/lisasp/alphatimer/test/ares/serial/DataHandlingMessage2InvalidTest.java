package org.lisasp.alphatimer.test.ares.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.DataHandlingMessage2DroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnknownMessageDroppedEvent;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageConverter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataHandlingMessage2InvalidTest {

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

        inputCollector = new InputCollector("Test", dateTimeFacade);
        inputCollector.register(messageConverter);
    }

    @AfterEach
    void cleanUp() {
        inputCollector = null;
        listener = null;
    }

    @Test
    void sendCorruptedMessage2_1() {
        // bytes at index 3 and 7 are required to detect message 2
        byte[] message2modified = Arrays.copyOf(DataHandlingMessageTestData.message2, DataHandlingMessageTestData.message2.length);
        message2modified[3] = 0x00;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new UnknownMessageDroppedEvent(TIMESTAMP, "Test", message2modified)), listener.received);
    }

    @Test
    void sendCorruptedMessage2_2() {
        // bytes at index 3 and 7 are required to detect message 2
        byte[] message2modified = Arrays.copyOf(DataHandlingMessageTestData.message2, DataHandlingMessageTestData.message2.length);
        message2modified[7] = 0x00;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new UnknownMessageDroppedEvent(TIMESTAMP, "Test", message2modified)), listener.received);
    }

    @ParameterizedTest
    @ValueSource(bytes = {4, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19})
    void sendMessage2WithInvalidByte0x00(int byteIndex) {
        byte[] message2modified = Arrays.copyOf(DataHandlingMessageTestData.message2, DataHandlingMessageTestData.message2.length);
        message2modified[byteIndex] = 0x00;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2DroppedEvent(TIMESTAMP, "Test", expectedMessage(message2modified, byteIndex), message2modified)), listener.received);
    }

    @ParameterizedTest
    @ValueSource(bytes = {4, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19})
    void sendMessage2WithInvalidByte0x44(int byteIndex) {
        byte[] message2modified = Arrays.copyOf(DataHandlingMessageTestData.message2, DataHandlingMessageTestData.message2.length);
        message2modified[byteIndex] = 0x44;

        for (byte b : message2modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new DataHandlingMessage2DroppedEvent(TIMESTAMP, "Test", expectedMessage(message2modified, byteIndex), message2modified)), listener.received);
    }

    /**
     * Mirrors the exact message-building logic of DataHandlingMessage2Parser / ByteArrayUtils
     * for the field that contains byteIndex, so the expected message does not need to be
     * hand-transcribed (it would otherwise need to embed raw control bytes such as 0x00).
     */
    private static String expectedMessage(byte[] data, int byteIndex) {
        if (byteIndex == 4) {
            return String.format("%d must be the ascii-representation of a digit.", data[4]);
        }
        if (byteIndex == 5 || byteIndex == 6) {
            return String.format("%d, %d must be the ascii-representation of a one or two digits number.", data[5], data[6]);
        }
        if (byteIndex == 19) {
            return String.format("%d must be the ascii-representation of a 'time info'.", data[19]);
        }
        // indices 8-18: time field, raw (untrimmed) 11-byte slice at data[8..18]
        return String.format("Data at indices 8-18 must contain a valid time: '%s'", new String(data, 8, 11));
    }
}
