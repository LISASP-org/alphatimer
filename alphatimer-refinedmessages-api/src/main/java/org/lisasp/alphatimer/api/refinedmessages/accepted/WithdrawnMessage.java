package org.lisasp.alphatimer.api.refinedmessages.accepted;

import lombok.Value;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;

import java.time.LocalDateTime;

/** The swimmer of the lane was withdrawn from the heat (also when reported sick). */
@Value
public class WithdrawnMessage implements RefinedMessage {
    private final LocalDateTime timestamp;
    private final String competition;
    private final short event;
    private final byte heat;
    private final String round;
    private final byte lane;
    /** true when the source withdraws the penalty it sent before */
    private final boolean removed;
}
