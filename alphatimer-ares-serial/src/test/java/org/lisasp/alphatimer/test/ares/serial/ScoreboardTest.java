package org.lisasp.alphatimer.test.ares.serial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnknownMessageDroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.messages.ScoreboardMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.ScoreboardCommand;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageConverter;
import org.lisasp.basics.jre.date.DateTimeFacade;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreboardTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2023, 5, 5, 13, 10);

    private static final String RUNNING_TIME = "\u0001\u0014R02\u0002\u0008  \u0002   13:10.9  \u0004";
    private static final String RESULT_LINE = "\u0001\u0014S02\u0002\u0008\n2 \u0002 3 13:25.82 \u0004";
    private static final String COMMAND_L = "\u0001\u0014L0\u0004";
    private static final String UNKNOWN_COMMAND = "\u0001\u0014X0\u0004";

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

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private void send(String text) {
        for (byte b : bytes(text)) {
            inputCollector.accept(b);
        }
    }

    @Test
    void runningTime() {
        send(RUNNING_TIME);

        assertEquals(List.of(new ScoreboardMessage(TIMESTAMP, "TestWK", ScoreboardCommand.RunningTime, bytes(RUNNING_TIME))), listener.received);
    }

    @Test
    void resultLine() {
        send(RESULT_LINE);

        assertEquals(List.of(new ScoreboardMessage(TIMESTAMP, "TestWK", ScoreboardCommand.ResultLine, bytes(RESULT_LINE))), listener.received);
    }

    @Test
    void commandL() {
        send(COMMAND_L);

        assertEquals(List.of(new ScoreboardMessage(TIMESTAMP, "TestWK", ScoreboardCommand.UnknownValueL, bytes(COMMAND_L))), listener.received);
    }

    @Test
    void anUnknownCommandIsAnUnknownMessage() {
        send(UNKNOWN_COMMAND);

        assertEquals(List.of(new UnknownMessageDroppedEvent(TIMESTAMP, "TestWK", bytes(UNKNOWN_COMMAND))), listener.received);
    }
}
