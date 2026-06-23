package org.lisasp.alphatimer.test.ares.serial;

import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageListener;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;

import java.util.ArrayList;
import java.util.List;

class TestDataHandlingMessageListener implements DataHandlingMessageListener {

    final List<DataHandlingMessage> received = new ArrayList<>();

    @Override
    public void accept(DataHandlingMessage event) {
        received.add(event);
    }
}
