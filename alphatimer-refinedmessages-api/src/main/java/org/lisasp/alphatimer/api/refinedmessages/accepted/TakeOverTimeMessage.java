package org.lisasp.alphatimer.api.refinedmessages.accepted;

import lombok.Value;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;

import java.time.LocalDateTime;
import java.util.OptionalInt;

@Value
public class TakeOverTimeMessage implements RefinedMessage {
    private final LocalDateTime timestamp;
    private final String competition;
    private final short event;
    private final byte heat;
    private final RefinedMessageType messageType;
    private final byte lane;
    private final byte currentLap;
    private final TimeMarker timeMarker;
    private final TimeType timeType;
    private final int timeInMillis;

    @Override
    public OptionalInt getDeviceTimeInMillis() {
        return timeInMillis > 0 ? OptionalInt.of(timeInMillis) : OptionalInt.empty();
    }
}
