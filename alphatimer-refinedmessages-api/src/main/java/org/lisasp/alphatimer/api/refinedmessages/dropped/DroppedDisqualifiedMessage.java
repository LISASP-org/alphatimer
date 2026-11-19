package org.lisasp.alphatimer.api.refinedmessages.dropped;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;

import java.time.LocalDateTime;

@Value
@RequiredArgsConstructor
public class DroppedDisqualifiedMessage implements DroppedRefinedMessage {
    private final LocalDateTime timestamp;
    private final String competition;
    private final MessageType messageType;
    private final KindOfTime kindOfTime;
    private final TimeType timeType;
    private final String usedLanes;
    private final byte lapCount;
    private final short event;
    private final byte heat;
    private final byte rank;
    private final RankInfo rankInfo;
    private final byte lane;
    private final byte currentLap;
    private final int timeInMillis;
    private final TimeInfo timeInfo;
    private final TimeMarker timeMarker;

    public DroppedDisqualifiedMessage(DataHandlingMessage message) {
        this(message.getTimestamp(),
             message.getCompetition(),
             message.getMessageType(),
             message.getKindOfTime(),
             message.getTimeType(),
             message.getUsedLanes().toValue(),
             message.getLapCount(),
             message.getEvent(),
             message.getHeat(),
             message.getRank(),
             message.getRankInfo(),
             message.getLane(),
             message.getCurrentLap(),
             message.getTimeInMillis(),
             message.getTimeInfo(),
             message.getTimeMarker());
    }
}
