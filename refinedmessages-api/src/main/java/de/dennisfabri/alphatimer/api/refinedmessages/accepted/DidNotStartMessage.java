package de.dennisfabri.alphatimer.api.refinedmessages.accepted;

import de.dennisfabri.alphatimer.api.refinedmessages.RefinedMessage;
import lombok.Value;

@Value
public class DidNotStartMessage implements RefinedMessage {
    private final short event;
    private final byte heat;
    private final byte lane;
}