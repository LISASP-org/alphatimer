package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.RunningClock;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class LiveTexts {

    private LiveTexts() {
    }

    public static String title(HeatDto heat) {
        return heat == null ? I18n.get("live.none") : I18n.get("live.title", String.valueOf(heat.event()), String.valueOf(heat.heat()));
    }

    public static String clock(HeatDto heat, LocalDateTime now, boolean recording) {
        return TimeFormat.running(RunningClock.millis(heat, now, recording));
    }

    /**
     * "start → end" as times of day; until the end arrives, the last message of the heat stands in for it. When the start
     * carried the time of day of the device, both follow that clock with the offset, the end by the time that has passed
     * since the start; otherwise they are the arrival times on the local clock.
     */
    public static String times(HeatDto heat, int timekeepingOffsetHours) {
        if (heat == null || heat.started() == null) {
            return "";
        }
        LocalTime start = heat.startTimeOfDayMillis() == null ? heat.started().toLocalTime() : DeviceClock.timeOfDay(heat.startTimeOfDayMillis(), timekeepingOffsetHours);
        String started = TimeFormat.clock(start) + " \u2192";
        LocalDateTime end = heat.ended() != null ? heat.ended() : heat.lastMessage();
        return end == null ? started : started + " " + TimeFormat.clock(start.plus(Duration.between(heat.started(), end)));
    }
}
