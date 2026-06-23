package org.lisasp.alphatimer.test.ares.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.Characters;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnknownMessageDroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnstructuredInputDroppedEvent;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageConverter;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 21, 14, 53);

    InputCollector inputCollector;
    TestDataInputEventListener listener;

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
    void acceptInput() {
        inputCollector.accept((byte) 0x01);
        inputCollector.accept((byte) 0x02);
        inputCollector.accept((byte) 0x03);
        inputCollector.accept((byte) 0x05);
        inputCollector.accept((byte) 0x06);

        assertEquals(List.of(), listener.received);
    }

    @Test
    void longUnstructuredInput() {
        for (int x = 0; x < 10000; x++) {
            inputCollector.accept((byte) 0x02);
        }

        byte[] fullBuffer = new byte[1024];
        java.util.Arrays.fill(fullBuffer, (byte) 0x02);
        List<org.lisasp.alphatimer.api.ares.serial.events.DataInputEvent> expected = new java.util.ArrayList<>();
        for (int x = 0; x < 9; x++) {
            expected.add(new UnstructuredInputDroppedEvent(TIMESTAMP, "TestWK", fullBuffer));
        }

        assertEquals(expected, listener.received);
    }

    @Test
    void close() {
        inputCollector.close();

        assertEquals(List.of(), listener.received);
    }

    @Test
    void closeWithData() {
        inputCollector.accept((byte) 0x01);
        inputCollector.accept((byte) 0x02);
        inputCollector.accept((byte) 0x03);
        inputCollector.accept((byte) 0x05);
        inputCollector.accept((byte) 0x06);

        inputCollector.close();

        assertEquals(List.of(new UnstructuredInputDroppedEvent(TIMESTAMP,
                                                                 "TestWK",
                                                                 new byte[]{0x01, 0x02, 0x03, 0x05, 0x06})), listener.received);
    }

    @Test
    void denyInputWithSingleEOT() {
        inputCollector.accept(Characters.EOT_EndOfText);

        assertEquals(List.of(new UnstructuredInputDroppedEvent(TIMESTAMP,
                                                                 "TestWK",
                                                                 new byte[]{Characters.EOT_EndOfText})), listener.received);
    }

    @Test
    void denyInputWithEmptyMessage() {
        inputCollector.accept((byte) 0x01);
        inputCollector.accept(Characters.EOT_EndOfText);

        assertEquals(List.of(new UnstructuredInputDroppedEvent(TIMESTAMP,
                                                                 "TestWK",
                                                                 new byte[]{0x01, Characters.EOT_EndOfText})), listener.received);
    }

    @Test
    void denyInputWithEmptyMessageAndHighBit() {
        inputCollector.accept((byte) 0x01);
        inputCollector.accept((byte) (Characters.EOT_EndOfText - 128));

        assertEquals(List.of(new UnstructuredInputDroppedEvent(TIMESTAMP,
                                                                 "TestWK",
                                                                 new byte[]{0x01, Characters.EOT_EndOfText})), listener.received);
    }

    @Test
    void denyInputWithIncompleteMessage() {
        inputCollector.accept((byte) 0x02);
        inputCollector.accept((byte) 0x03);
        inputCollector.accept(Characters.EOT_EndOfText);

        assertEquals(List.of(new UnstructuredInputDroppedEvent(TIMESTAMP,
                                                                 "TestWK",
                                                                 new byte[]{0x02, 0x03, Characters.EOT_EndOfText})), listener.received);
    }

    @Test
    void denyInputWithInvalidMessage() {
        inputCollector.accept(Characters.SOH_StartOfHeader);
        inputCollector.accept((byte) 0x03);
        inputCollector.accept(Characters.EOT_EndOfText);

        assertEquals(List.of(new UnknownMessageDroppedEvent(TIMESTAMP,
                                                              "TestWK",
                                                              new byte[]{Characters.SOH_StartOfHeader, 0x03, Characters.EOT_EndOfText})), listener.received);
    }

    @Test
    void emitNoOutputAfterStartSymbolWithoutPreviousInput() {
        inputCollector.accept(Characters.SOH_StartOfHeader);

        assertEquals(List.of(), listener.received);
    }

    @Test
    void emitOutputAfterStartSymbolWithPreviousInput() {
        inputCollector.accept(Characters.SPACE);
        inputCollector.accept(Characters.SOH_StartOfHeader);

        assertEquals(List.of(new UnstructuredInputDroppedEvent(TIMESTAMP,
                                                                 "TestWK",
                                                                 new byte[]{Characters.SPACE})), listener.received);
    }
}
