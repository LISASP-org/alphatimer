package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatStatus;
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
        String started = TimeFormat.clock(timeOfDay(heat, heat.started(), timekeepingOffsetHours)) + " \u2192";
        LocalDateTime end = heat.ended() != null ? heat.ended() : heat.lastMessage();
        return end == null ? started : started + " " + endTime(heat, end, timekeepingOffsetHours);
    }

    static String endTime(HeatDto heat, LocalDateTime end, int timekeepingOffsetHours) {
        String time = TimeFormat.clock(timeOfDay(heat, end, timekeepingOffsetHours));
        return heat.status() == HeatStatus.FinishedEstimated ? I18n.get("time.estimated", time) : time;
    }

    static LocalTime timeOfDay(HeatDto heat, LocalDateTime moment, int timekeepingOffsetHours) {
        return shownMoment(heat, moment, timekeepingOffsetHours).toLocalTime();
    }

    /** A moment of a started heat on the clock that {@link #times} describes, on the day the heat started here. */
    static LocalDateTime shownMoment(HeatDto heat, LocalDateTime moment, int timekeepingOffsetHours) {
        LocalTime start = heat.startTimeOfDayMillis() == null ? heat.started().toLocalTime() : DeviceClock.timeOfDay(heat.startTimeOfDayMillis(), timekeepingOffsetHours);
        return heat.started().toLocalDate().atTime(start).plus(Duration.between(heat.started(), moment));
    }
}
