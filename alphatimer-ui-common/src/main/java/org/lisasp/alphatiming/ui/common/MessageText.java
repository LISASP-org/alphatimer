package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;

/** One line of the message log for an interpreted message: event, heat, lane, kind, type, time and rank. */
public final class MessageText {

    private MessageText() {
    }

    public static String processingError(Exception ex) {
        return I18n.get("log.processingError", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }

    public static String describe(DataHandlingMessage message) {
        StringBuilder text = new StringBuilder(String.format("E%d H%d L%d  %s  %s  %s",
                                                             message.getEvent(),
                                                             message.getHeat(),
                                                             message.getLane(),
                                                             message.getKindOfTime(),
                                                             message.getMessageType(),
                                                             time(message)));
        if (message.getRank() > 0) {
            text.append("  #").append(message.getRank());
        }
        return text.toString();
    }

    private static String time(DataHandlingMessage message) {
        int millis = message.getTimeInMillis();
        if (message.getKindOfTime() == KindOfTime.Start) {
            return String.format("%02d:%02d:%02d.%d", millis / 3_600_000, (millis / 60_000) % 60, (millis / 1000) % 60, (millis / 100) % 10);
        }
        return TimeFormat.time(millis);
    }
}
