package org.lisasp.alphatimer.api.refinedmessages.accepted;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;

import java.time.LocalDateTime;
import java.util.OptionalInt;

@Value
@AllArgsConstructor
public class StartMessage implements RefinedMessage {
    private final LocalDateTime timestamp;
    private final String competition;
    private final short event;
    private final byte heat;
    private final String round;
    private final RefinedMessageType messageType;
    private final byte lapCount;
    private final byte lane;
    private final int timeOfDayInMillis;
    private final RefinedTimeType timeType;

    public StartMessage(LocalDateTime timestamp, String competition, short event, byte heat, RefinedMessageType messageType, byte lapCount, byte lane, int timeOfDayInMillis, RefinedTimeType timeType) {
        this(timestamp, competition, event, heat, "", messageType, lapCount, lane, timeOfDayInMillis, timeType);
    }

    @Override
    public OptionalInt getDeviceTimeInMillis() {
        return timeOfDayInMillis > 0 ? OptionalInt.of(timeOfDayInMillis) : OptionalInt.empty();
    }
}
