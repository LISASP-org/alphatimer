package org.lisasp.alphatimer.api.refinedmessages;

import java.time.LocalDateTime;
import java.util.OptionalInt;

public interface RefinedMessage {

    String getCompetition();

    short getEvent();

    byte getHeat();

    LocalDateTime getTimestamp();

    /**
     * The time value the device sent with the incoming message, where it has one: the time of day for a start, the race
     * time for a split or finish, the time of a take-over. Empty for messages without a time. Unlike
     * {@link #getTimestamp()}, which is the arrival on the local clock, this is a value of the device.
     */
    default OptionalInt getDeviceTimeInMillis() {
        return OptionalInt.empty();
    }
}
