package de.dennisfabri.alphatimer.refinedmessages;

import de.dennisfabri.alphatimer.api.protocol.events.messages.DataHandlingMessage;
import de.dennisfabri.alphatimer.api.protocol.events.messages.enums.*;
import de.dennisfabri.alphatimer.api.refinedmessages.RefinedMessage;
import de.dennisfabri.alphatimer.api.refinedmessages.accepted.TimeMessage;
import de.dennisfabri.alphatimer.api.refinedmessages.dropped.DroppedTimeMessage;

import java.util.function.Consumer;

public class TimeParser implements Parser {

    private EnumUtils utils = new EnumUtils();

    @Override
    public void accept(DataHandlingMessage message, Consumer<RefinedMessage> resultCollector) {
        if ((message.getMessageType() == MessageType.CurrentRaceResults || message.getMessageType() == MessageType.PreviousRaceResults || message.getMessageType() == MessageType.OnLineTime) && (message.getKindOfTime() == KindOfTime.Finish || message.getKindOfTime() == KindOfTime.SplitTime)) {
            if (isValid(message)) {
                resultCollector.accept(new TimeMessage(message.getEvent(),
                                                       message.getHeat(),
                                                       utils.convertMessageType(message.getMessageType()),
                                                       utils.convertKindOfTime(message.getKindOfTime()),
                                                       message.getLane(),
                                                       message.getCurrentLap(),
                                                       message.getLapCount(),
                                                       message.getRank(),
                                                       message.getTimeInMillis(),
                                                       utils.convertTimeType(message.getTimeType())));
            } else {
                resultCollector.accept(new DroppedTimeMessage(message));
            }
        }
    }

    private boolean isValid(DataHandlingMessage message) {
        return //message.getCurrentLap() > 0 &&
                //message.getLapCount() == message.getCurrentLap() &&
                // message.getLapCount() > 0 &&
                message.getLane() > 0 &&
                        // message.getRank() > 0 &&
                        message.getRankInfo() == RankInfo.Normal &&
                        message.getTimeMarker() == TimeMarker.Empty &&
                        ((message.getTimeInfo() == TimeInfo.Normal && message.getTimeType() == TimeType.Empty) ||
                                (message.getTimeInfo() == TimeInfo.Normal && message.getTimeType() == TimeType.DeletedTime) ||
                                (message.getTimeInfo() == TimeInfo.Normal && message.getTimeType() == TimeType.CorrectedTime) ||
                                (message.getTimeInfo() == TimeInfo.Manual && message.getTimeType() == TimeType.ManualTime));
    }

}
