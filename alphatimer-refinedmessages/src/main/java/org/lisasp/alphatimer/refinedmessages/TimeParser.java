package org.lisasp.alphatimer.refinedmessages;

import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedTimeMessage;

import java.util.function.Consumer;

public class TimeParser implements Parser {

    private final EnumUtils utils = new EnumUtils();

    @Override
    public void accept(DataHandlingMessage message, Consumer<RefinedMessage> resultCollector) {
        if (utils.isRaceTime(message)
                && message.getTimeInfo() != TimeInfo.Backup) {
            if (isValid(message)) {
                resultCollector.accept(new TimeMessage(
                        message.getTimestamp(),
                        message.getCompetition(),
                        message.getEvent(),
                        message.getHeat(),
                        utils.convertMessageType(message.getMessageType()),
                        utils.convertKindOfTime(message.getKindOfTime()),
                        message.getLane(),
                        message.getCurrentLap(),
                        message.getLapCount(),
                        message.getRank(),
                        message.getTimeInMillis(),
                        utils.convertTimeType(message.getTimeInfo(), message.getTimeType())));
            } else {
                resultCollector.accept(new DroppedTimeMessage(message));
            }
        }
    }

    private boolean isValid(DataHandlingMessage message) {
        return message.getLane() > 0 &&
                message.getRankInfo() == RankInfo.Normal &&
                message.getTimeMarker() == TimeMarker.Empty &&
                isAcceptedTime(message.getTimeInfo(), message.getTimeType());
    }

    /** ARES marks a manual time in the time type, the time info or both; a time edited from the keyboard in the time info. */
    private static boolean isAcceptedTime(TimeInfo timeInfo, TimeType timeType) {
        if (timeType == TimeType.DeletedTime) {
            return true;
        }
        return switch (timeInfo) {
            case Normal -> timeType == TimeType.Empty || timeType == TimeType.DeletedTime || timeType == TimeType.CorrectedTime
                    || timeType == TimeType.ManualTime;
            case Manual -> timeType == TimeType.Empty || timeType == TimeType.ManualTime;
            case Edited -> timeType == TimeType.Empty || timeType == TimeType.CorrectedTime;
            default -> false;
        };
    }

}