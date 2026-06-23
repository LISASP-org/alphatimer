package org.lisasp.alphatimer.test.ares.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.Characters;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnknownMessageDroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.messages.Ping;
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
class PingTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 21, 15, 51);

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
    void sendPing() {
        for (byte b : DataHandlingMessageTestData.ping) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new Ping(TIMESTAMP, "TestWK", new byte[]{0x54, 0x50})), listener.received);
    }

    @Test
    void sendPingInvalid1() {
        byte[] modifiedPing = Arrays.copyOf(DataHandlingMessageTestData.ping, DataHandlingMessageTestData.ping.length);
        modifiedPing[1] = 0x00;

        for (byte b : modifiedPing) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new UnknownMessageDroppedEvent(TIMESTAMP,
                                                            "TestWK",
                                                            new byte[]{Characters.SOH_StartOfHeader, 0x00, 0x39, Characters.DC4_Command, 0x54, 0x50, Characters.EOT_EndOfText})), listener.received);
    }

    @Test
    void sendPingInvalid2() {
        byte[] modifiedPing = Arrays.copyOf(DataHandlingMessageTestData.ping, DataHandlingMessageTestData.ping.length);
        modifiedPing[2] = 0x00;

        for (byte b : modifiedPing) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new UnknownMessageDroppedEvent(TIMESTAMP,
                                                            "TestWK",
                                                            new byte[]{Characters.SOH_StartOfHeader, Characters.DC2_Periphery, 0x00, Characters.DC4_Command, 0x54, 0x50, Characters.EOT_EndOfText})), listener.received);
    }

    @Test
    void sendPingInvalid3() {
        byte[] modifiedPing = Arrays.copyOf(DataHandlingMessageTestData.ping, DataHandlingMessageTestData.ping.length);
        modifiedPing[3] = 0x00;

        for (byte b : modifiedPing) {
            inputCollector.accept(b);
        }

        assertEquals(List.of(new UnknownMessageDroppedEvent(TIMESTAMP,
                                                            "TestWK",
                                                            new byte[]{Characters.SOH_StartOfHeader, Characters.DC2_Periphery, 0x39, 0x00, 0x54, 0x50, Characters.EOT_EndOfText})), listener.received);
    }
}
