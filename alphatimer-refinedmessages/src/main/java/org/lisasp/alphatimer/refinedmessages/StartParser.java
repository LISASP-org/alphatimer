package org.lisasp.alphatimer.refinedmessages;

import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.StartMessage;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedStartMessage;

import java.util.function.Consumer;

public class StartParser implements Parser {

    private final EnumUtils utils = new EnumUtils();

    @Override
    public void accept(DataHandlingMessage message, Consumer<RefinedMessage> resultCollector) {
        if (utils.carriesRaceTimes(message.getMessageType()) && message.getKindOfTime() == KindOfTime.Start) {
            if (isValid(message)) {
                resultCollector.accept(new StartMessage(message.getTimestamp(),
                                                        message.getCompetition(),
                                                        message.getEvent(),
                                                        message.getHeat(),
                                                        utils.convertMessageType(message.getMessageType()),
                                                        message.getLapCount(),
                                                        message.getLane(),
                                                        message.getTimeInMillis(),
                                                        utils.convertTimeType(message.getTimeType())));
            } else {
                resultCollector.accept(new DroppedStartMessage(message));
            }
        }
    }

    private boolean isValid(DataHandlingMessage message) {
        return message.getCurrentLap() == 0 &&
                // message.getLapCount() > 0 &&
                // message.getLane() > 0 &&
                message.getRank() == 0 &&
                message.getRankInfo() == RankInfo.Normal &&
                message.getTimeMarker() == TimeMarker.Empty &&
                // message.getTimeInfo() == TimeInfo.Normal &&
                (message.getTimeType() == TimeType.Empty || message.getTimeType() == TimeType.CorrectedTime || message.getTimeType() == TimeType.ManualTime);
    }
}
