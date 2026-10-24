package org.lisasp.alphatiming.ui.common;

import java.text.DecimalFormatSymbols;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TimeFormat {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private TimeFormat() {
    }

    public static String time(int millis) {
        return time(millis, Locale.getDefault());
    }

    /** A lane time in hundredths: {@code 1:05.43}, {@code 5.00}; empty for no time. The decimal separator follows the locale. */
    public static String time(int millis, Locale locale) {
        if (millis <= 0) {
            return "";
        }
        char separator = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
        int hundredths = millis / 10;
        int minutes = hundredths / 6000;
        int seconds = (hundredths / 100) % 60;
        int rest = hundredths % 100;
        return minutes > 0 ? String.format("%d:%02d%c%02d", minutes, seconds, separator, rest) : String.format("%d%c%02d", seconds, separator, rest);
    }

    public static String running(long millis) {
        return running(millis, Locale.getDefault());
    }

    /** The running time of a heat in tenths: {@code 0:05.5}, {@code 1:02:05.0}. */
    public static String running(long millis, Locale locale) {
        char separator = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
        long tenths = Math.max(0, millis) / 100;
        long hours = tenths / 36000;
        long minutes = (tenths / 600) % 60;
        long seconds = (tenths / 10) % 60;
        long rest = tenths % 10;
        return hours > 0 ? String.format("%d:%02d:%02d%c%d", hours, minutes, seconds, separator, rest) : String.format("%d:%02d%c%d", minutes, seconds, separator, rest);
    }

    public static String clock(LocalTime time) {
        return CLOCK.format(time);
    }
}
