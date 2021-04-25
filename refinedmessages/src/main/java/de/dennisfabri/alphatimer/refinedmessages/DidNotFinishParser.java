package de.dennisfabri.alphatimer.refinedmessages;

import de.dennisfabri.alphatimer.api.protocol.events.messages.DataHandlingMessage;
import de.dennisfabri.alphatimer.api.protocol.events.messages.enums.*;
import de.dennisfabri.alphatimer.api.refinedmessages.RefinedMessage;
import de.dennisfabri.alphatimer.api.refinedmessages.accepted.DidNotFinishMessage;
import de.dennisfabri.alphatimer.api.refinedmessages.dropped.DroppedDidNotFinishMessage;

import java.util.function.Consumer;

public class DidNotFinishParser implements Parser {
    @Override
    public void accept(DataHandlingMessage message, Consumer<RefinedMessage> resultCollector) {
        if (message.getMessageType() == MessageType.CurrentRaceResults && message.getTimeMarker() == TimeMarker.DidNotFinish) {
            if (isValid(message)) {
                resultCollector.accept(new DidNotFinishMessage(message.getEvent(),
                                                               message.getHeat(),
                                                               message.getLane()
                ));
            } else {
                resultCollector.accept(new DroppedDidNotFinishMessage(message));
            }
        }
    }

    private boolean isValid(DataHandlingMessage message) {
        return message.getCurrentLap() == 0 &&
                message.getLapCount() == 0 &&
                message.getLane() > 0 &&
                message.getRank() == 0 &&
                message.getKindOfTime() == KindOfTime.Empty &&
                message.getRankInfo() == RankInfo.Normal &&
                message.getTimeInfo() == TimeInfo.Normal &&
                message.getTimeType() == TimeType.Empty &&
                message.getTimeInMillis() == 0;
    }
}
