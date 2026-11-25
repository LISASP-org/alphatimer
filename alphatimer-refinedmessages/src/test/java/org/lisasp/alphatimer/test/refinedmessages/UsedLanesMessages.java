package org.lisasp.alphatimer.test.refinedmessages;

import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.LaneNumbering;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

final class UsedLanesMessages {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 1, 10, 0);

    private UsedLanesMessages() {
    }

    static UsedLanesMessage usedLanes(int event, int heat, String aresFlags) {
        List<Integer> lanes = new ArrayList<>();
        for (int flag = 0; flag < aresFlags.length(); flag++) {
            if (aresFlags.charAt(flag) == '1') {
                lanes.add(flag + 1);
            }
        }
        return new UsedLanesMessage(TIMESTAMP, "TestWK", (short) event, (byte) heat, "", lanes, LaneNumbering.OneBased);
    }
}
