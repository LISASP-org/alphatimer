package org.lisasp.alphatimer.test.refinedmessages;

import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessageListener;

import java.util.ArrayList;
import java.util.List;

public class TestRefinedMessageListener implements RefinedMessageListener {

    public final List<RefinedMessage> received = new ArrayList<>();

    @Override
    public void accept(RefinedMessage event) {
        received.add(event);
    }
}
