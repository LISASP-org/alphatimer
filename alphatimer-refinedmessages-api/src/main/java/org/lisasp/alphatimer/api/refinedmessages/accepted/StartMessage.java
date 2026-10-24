package org.lisasp.alphatimer.api.refinedmessages.accepted;

import lombok.Value;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;

import java.time.LocalDateTime;
import java.util.OptionalInt;

@Value
public class StartMessage implements RefinedMessage {
    private final LocalDateTime timestamp;
    private final String competition;
    private final short event;
    private final byte heat;
    private final RefinedMessageType messageType;
    private final byte lapCount;
    private final byte lane;
    private final int timeOfDayInMillis;
    private final RefinedTimeType timeType;

    @Override
    public OptionalInt getDeviceTimeInMillis() {
        return timeOfDayInMillis > 0 ? OptionalInt.of(timeOfDayInMillis) : OptionalInt.empty();
    }
}
