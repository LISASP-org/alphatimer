package org.lisasp.alphatimer.test.heats;

import org.lisasp.alphatimer.heats.HeatListener;
import org.lisasp.alphatimer.heats.api.HeatDto;

import java.util.ArrayList;
import java.util.List;

public class TestHeatListener implements HeatListener {

    public final List<HeatDto> received = new ArrayList<>();

    @Override
    public void accept(HeatDto event) {
        received.add(event);
    }
}
