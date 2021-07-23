package org.lisasp.alphatimer.api.refinedmessages.accepted;

import lombok.Value;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;

import java.time.LocalDateTime;

@Value
public class TimeMessage implements RefinedMessage {
    private final LocalDateTime timestamp;
    private final String competition;
    private final short event;
    private final byte heat;
    private final RefinedMessageType messageType;
    private final RefinedKindOfTime kindOfTime;
    private final byte lane;
    private final byte currentLap;
    private final byte lapCount;
    private final byte rank;
    private final int timeOfDayInMillis;
    private final RefinedTimeType timeType;
}
