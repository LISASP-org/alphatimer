package org.lisasp.alphatimer.refinedmessages;

import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.BackupTimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedBackupTimeMessage;

import java.util.function.Consumer;

public class BackupTimeParser implements Parser {

    private final EnumUtils utils = new EnumUtils();

    @Override
    public void accept(DataHandlingMessage message, Consumer<RefinedMessage> resultCollector) {
        if (isBackupTime(message)) {
            if (isValid(message)) {
                resultCollector.accept(new BackupTimeMessage(message.getTimestamp(),
                                                             message.getCompetition(),
                                                             message.getEvent(),
                                                             message.getHeat(),
                                                             utils.convertMessageType(message.getMessageType()),
                                                             message.getLane(),
                                                             message.getCurrentLap(),
                                                             message.getTimeInMillis()));
            } else {
                resultCollector.accept(new DroppedBackupTimeMessage(message));
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

    private boolean isValid(DataHandlingMessage message) {
        return message.getLane() > 0 && message.getCurrentLap() > 0 && message.getTimeInMillis() > 0
                && message.getTimeType() != TimeType.DeletedTime && message.getTimeMarker() == TimeMarker.Empty;
    }
}
