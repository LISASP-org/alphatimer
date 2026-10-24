package org.lisasp.alphatimer.refinedmessages;

import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TakeOverTimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedTakeOverTimeMessage;

import java.util.function.Consumer;

public class TakeOverTimeParser implements Parser {

    private final EnumUtils utils = new EnumUtils();

    @Override
    public void accept(DataHandlingMessage message, Consumer<RefinedMessage> resultCollector) {
        if ((message.getMessageType() == MessageType.CurrentRaceResults || message.getMessageType() == MessageType.PreviousRaceResults || message.getMessageType() == MessageType.OnLineTime) && message.getKindOfTime() == KindOfTime.TakeOverTime) {
            if (isValid(message)) {
                resultCollector.accept(new TakeOverTimeMessage(message.getTimestamp(),
                                                               message.getCompetition(),
                                                               message.getEvent(),
                                                               message.getHeat(),
                                                               utils.convertMessageType(message.getMessageType()),
                                                               message.getLane(),
                                                               message.getCurrentLap(),
                                                               message.getTimeMarker(),
                                                               message.getTimeType(),
                                                               message.getTimeInMillis()));
            } else {
                resultCollector.accept(new DroppedTakeOverTimeMessage(message));
            }
        }
    }

    private boolean isValid(DataHandlingMessage message) {
        return message.getCurrentLap() > 0 &&
                message.getLapCount() > message.getCurrentLap() &&
                message.getLane() > 0 &&
                message.getRank() == 0 &&
                message.getTimeInfo() == TimeInfo.Normal &&
                ((message.getRankInfo() == RankInfo.Normal) &&
                        (message.getTimeMarker() == TimeMarker.Plus || message.getTimeMarker() == TimeMarker.Minus) &&
                        (message.getTimeType() == TimeType.PlatformTimeAfterTouchpadTime || message.getTimeType() == TimeType.PlatformTimeBeforeTouchpadTime))
                || (message.getRankInfo() == RankInfo.Disqualified && message.getTimeMarker() == TimeMarker.Empty && message.getTimeType() == TimeType.Empty)
                ;
    }
}
