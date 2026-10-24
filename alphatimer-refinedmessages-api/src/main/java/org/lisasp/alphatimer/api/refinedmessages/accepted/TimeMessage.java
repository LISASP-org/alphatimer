package org.lisasp.alphatimer.api.refinedmessages.accepted;

import lombok.Value;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;

import java.time.LocalDateTime;
import java.util.OptionalInt;

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
    private final int timeInMillis;
    private final RefinedTimeType timeType;

    @Override
    public OptionalInt getDeviceTimeInMillis() {
        return timeInMillis > 0 ? OptionalInt.of(timeInMillis) : OptionalInt.empty();
    }
}
