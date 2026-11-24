package org.lisasp.alphatimer.refinedmessages;

import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeOrigin;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedTimeMessage;

import java.util.function.Consumer;

public class BackupTimeParser implements Parser {

    private final EnumUtils utils = new EnumUtils();

    @Override
    public void accept(DataHandlingMessage message, Consumer<RefinedMessage> resultCollector) {
        if (isBackupTime(message)) {
            if (isValid(message)) {
                resultCollector.accept(new TimeMessage(message.getTimestamp(),
                                                       message.getCompetition(),
                                                       message.getEvent(),
                                                       message.getHeat(),
                                                       utils.convertMessageType(message.getMessageType()),
                                                       kindOf(message),
                                                       message.getLane(),
                                                       message.getCurrentLap(),
                                                       message.getLapCount(),
                                                       message.getRank(),
                                                       message.getTimeInMillis(),
                                                       message.getTimeType() == TimeType.DeletedTime ? RefinedTimeType.DeletedTime : RefinedTimeType.Normal,
                                                       RefinedTimeOrigin.Backup));
            } else {
                resultCollector.accept(new DroppedTimeMessage(message));
            }
        }
    }

    /** Message type 7, or a time of a result with backup times that ARES marks with time info B. */
    private boolean isBackupTime(DataHandlingMessage message) {
        if (message.getMessageType() == MessageType.BackupTime) {
            return message.getKindOfTime() == KindOfTime.Backup;
        }
        return utils.isRaceTime(message) && message.getTimeInfo() == TimeInfo.Backup;
    }

    /** Message type 7 names no kind of time; it is a finish when it belongs to the last lap. */
    private RefinedKindOfTime kindOf(DataHandlingMessage message) {
        if (message.getKindOfTime() == KindOfTime.Backup) {
            return message.getCurrentLap() == message.getLapCount() ? RefinedKindOfTime.Finish : RefinedKindOfTime.SplitTime;
        }
        return utils.convertKindOfTime(message.getKindOfTime());
    }

    private boolean isValid(DataHandlingMessage message) {
        return message.getLane() > 0 && message.getCurrentLap() > 0 && message.getTimeInMillis() > 0 && message.getTimeMarker() == TimeMarker.Empty;
    }
}
