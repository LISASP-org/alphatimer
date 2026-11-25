package org.lisasp.alphatimer.refinedmessages;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.LaneNumbering;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class UsedLanesParser implements Parser {

    private static final int ARES_LANES = 10;

    @Override
    public void accept(DataHandlingMessage message, Consumer<RefinedMessage> resultCollector) {
        if (message.getMessageType() == MessageType.ReadyToStart || message.getMessageType() == MessageType.OnLineTime) {
            UsedLanes usedLanes = message.getUsedLanes();
            if (usedLanes.size() != ARES_LANES) {
                log.warn("Used lanes of event {} heat {} dropped: {} flags instead of {}", message.getEvent(), message.getHeat(), usedLanes.size(),
                         ARES_LANES);
                return;
            }
            resultCollector.accept(new UsedLanesMessage(message.getTimestamp(),
                                                        message.getCompetition(),
                                                        message.getEvent(),
                                                        message.getHeat(),
                                                        "",
                                                        lanes(usedLanes),
                                                        LaneNumbering.OneBased));
        }
    }

    private static List<Integer> lanes(UsedLanes usedLanes) {
        List<Integer> lanes = new ArrayList<>();
        for (int flag = 0; flag < ARES_LANES; flag++) {
            if (usedLanes.isUsed(flag)) {
                lanes.add(flag + 1);
            }
        }
        return lanes;
    }
}
