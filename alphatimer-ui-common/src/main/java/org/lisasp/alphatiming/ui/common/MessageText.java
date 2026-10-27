package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;

import java.time.LocalTime;

/** One line of the message log for an interpreted message: event, heat, lane, kind, type, time and rank. */
public final class MessageText {

    private MessageText() {
    }

    public static String processingError(Exception ex) {
        return I18n.get("log.processingError", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }

    public static String describe(DataHandlingMessage message, int timekeepingOffsetHours) {
        StringBuilder text = new StringBuilder(String.format("E%d H%d L%d  %s  %s  %s",
                                                             message.getEvent(),
                                                             message.getHeat(),
                                                             message.getLane(),
                                                             message.getKindOfTime(),
                                                             message.getMessageType(),
                                                             time(message, timekeepingOffsetHours)));
        if (message.getRank() > 0) {
            text.append("  #").append(message.getRank());
        }
        return text.toString();
    }

    private static String time(DataHandlingMessage message, int timekeepingOffsetHours) {
        int millis = message.getTimeInMillis();
        if (message.getKindOfTime() == KindOfTime.Start) {
            LocalTime time = DeviceClock.timeOfDay(millis, timekeepingOffsetHours);
            return String.format("%02d:%02d:%02d.%d", time.getHour(), time.getMinute(), time.getSecond(), time.getNano() / 100_000_000);
        }
        return TimeFormat.time(millis);
    }
}
