package org.lisasp.alphatimer.api.ares.serial.events.messages;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.lisasp.alphatimer.api.ares.serial.data.Bytes;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.ScoreboardCommand;

import java.time.LocalDateTime;

/**
 * What ARES sends to a scoreboard (running time, result lines) on the same line as the data handling messages. It
 * repeats what the data handling messages carry and is never refined.
 */
@Value
@RequiredArgsConstructor
public class ScoreboardMessage implements Message {
    private final LocalDateTime timestamp;
    private final String competition;
    private final ScoreboardCommand command;
    private final Bytes data;

    public ScoreboardMessage(LocalDateTime timestamp, String competition, ScoreboardCommand command, byte[] data) {
        this(timestamp, competition, command, new Bytes(data));
    }
}
