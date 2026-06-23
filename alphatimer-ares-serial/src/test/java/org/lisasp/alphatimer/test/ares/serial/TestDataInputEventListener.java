package org.lisasp.alphatimer.test.ares.serial;

import org.lisasp.alphatimer.api.ares.serial.DataInputEventListener;
import org.lisasp.alphatimer.api.ares.serial.events.DataInputEvent;

import java.util.ArrayList;
import java.util.List;

class TestDataInputEventListener implements DataInputEventListener {

    final List<DataInputEvent> received = new ArrayList<>();

    @Override
    public void accept(DataInputEvent event) {
        received.add(event);
    }
}
