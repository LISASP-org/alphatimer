package org.lisasp.alphatimer.api.refinedmessages.dropped;

import org.lisasp.alphatimer.api.protocol.events.messages.DataHandlingMessage;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class DroppedOfficialEndMessage extends DroppedRefinedMessage {
    public DroppedOfficialEndMessage(DataHandlingMessage message) {
        super(message);
    }
}