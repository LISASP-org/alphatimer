package org.lisasp.alphatimer.test.ares.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lisasp.alphatimer.api.ares.serial.DataInputEventListener;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.DataHandlingMessage1DroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnknownMessageDroppedEvent;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageConverter;
import org.lisasp.basics.jre.date.DateTimeFacade;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lisasp.alphatimer.test.ares.serial.DataHandlingMessageTestData.message1;

class DataHandlingMessage1InvalidTest {

    private InputCollector inputCollector;
    private TestDataInputEventListener listener;

    @BeforeEach
    void prepare() {
        DateTimeFacade dateTimeFacade = new DateTimeFacade() {
            @Override
            public LocalDateTime now() {
                return LocalDateTime.of(2021, 6, 21, 14, 53);
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
    void sendCorruptedMessage1_1() {
        // bytes at index 15 and 16 are required to detect message 1
        byte[] message1modified = Arrays.copyOf(message1, message1.length);
        message1modified[15] = 0x00;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new UnknownMessageDroppedEvent(
                LocalDateTime.of(2021, 6, 21, 14, 53),
                "Test",
                message1modified)), listener.received);
    }

    @Test
    void sendCorruptedMessage1_2() {
        // bytes at index 15 and 16 are required to detect message 1
        byte[] message1modified = Arrays.copyOf(message1, message1.length);
        message1modified[16] = 0x00;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new UnknownMessageDroppedEvent(
                LocalDateTime.of(2021, 6, 21, 14, 53),
                "Test",
                message1modified)), listener.received);
    }

    @ParameterizedTest
    @ValueSource(bytes = {3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 17, 18})
    void sendMessage1WithInvalidByte0x40(int byteIndex) {
        byte[] message1modified = Arrays.copyOf(message1, message1.length);
        message1modified[byteIndex] = 0x40;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(1, listener.received.size());
        assertEquals(DataHandlingMessage1DroppedEvent.class, listener.received.get(0).getClass());
        DataHandlingMessage1DroppedEvent droppedEvent = (DataHandlingMessage1DroppedEvent) listener.received.get(0);
        assertEquals(List.of(new DataHandlingMessage1DroppedEvent(
                LocalDateTime.of(2021, 6, 21, 14, 53),
                "Test",
                droppedEvent.getMessage(),
                message1modified)), listener.received);
    }

    @ParameterizedTest
    @ValueSource(bytes = {3, 4, 5})
    void sendMessage1WithInvalidByte0x38(int byteIndex) {
        byte[] message1modified = Arrays.copyOf(message1, message1.length);
        message1modified[byteIndex] = 0x38;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(1, listener.received.size());
        assertEquals(DataHandlingMessage1DroppedEvent.class, listener.received.get(0).getClass());
        DataHandlingMessage1DroppedEvent droppedEvent = (DataHandlingMessage1DroppedEvent) listener.received.get(0);
        assertEquals(List.of(new DataHandlingMessage1DroppedEvent(
                LocalDateTime.of(2021, 6, 21, 14, 53),
                "Test",
                droppedEvent.getMessage(),
                message1modified)), listener.received);
    }

    @ParameterizedTest
    @ValueSource(bytes = {3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 17, 18})
    void sendMessage1WithInvalidByte0x00(int byteIndex) {
        byte[] message1modified = Arrays.copyOf(message1, message1.length);
        message1modified[byteIndex] = 0x00;

        for (byte b : message1modified) {
            inputCollector.accept(b);
        }

        assertEquals(1, listener.received.size());
        assertEquals(DataHandlingMessage1DroppedEvent.class, listener.received.get(0).getClass());
        DataHandlingMessage1DroppedEvent droppedEvent = (DataHandlingMessage1DroppedEvent) listener.received.get(0);
        assertEquals(List.of(new DataHandlingMessage1DroppedEvent(
                LocalDateTime.of(2021, 6, 21, 14, 53),
                "Test",
                droppedEvent.getMessage(),
                message1modified)), listener.received);
    }
}
