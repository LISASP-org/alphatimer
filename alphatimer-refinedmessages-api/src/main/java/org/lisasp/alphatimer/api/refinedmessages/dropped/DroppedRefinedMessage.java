package org.lisasp.alphatimer.api.refinedmessages.dropped;

import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;

public interface DroppedRefinedMessage extends RefinedMessage {

    @Override
    default String getRound() {
        return "";
    }
}
