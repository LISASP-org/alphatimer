package org.lisasp.alphatimer.api.refinedmessages.accepted;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;

import java.time.LocalDateTime;

@Value
@AllArgsConstructor
public class DisqualifiedMessage implements RefinedMessage {
    private final LocalDateTime timestamp;
    private final String competition;
    private final short event;
    private final byte heat;
    private final String round;
    private final byte lane;
    /** true when the source withdraws the penalty it sent before */
    private final boolean removed;

    public DisqualifiedMessage(LocalDateTime timestamp, String competition, short event, byte heat, byte lane) {
        this(timestamp, competition, event, heat, "", lane, false);
    }
}
