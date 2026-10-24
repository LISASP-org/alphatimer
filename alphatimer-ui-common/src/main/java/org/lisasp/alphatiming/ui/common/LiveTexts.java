package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.RunningClock;

import java.time.LocalDateTime;

public final class LiveTexts {

    private LiveTexts() {
    }

    public static String title(HeatDto heat) {
        return heat == null ? I18n.get("live.none") : I18n.get("live.title", String.valueOf(heat.event()), String.valueOf(heat.heat()));
    }

    public static String clock(HeatDto heat, LocalDateTime now, boolean recording) {
        return TimeFormat.running(RunningClock.millis(heat, now, recording));
    }

    /** "start → end" as times of day; until the end arrives, the last message of the heat stands in for it. */
    public static String times(HeatDto heat) {
        if (heat == null || heat.started() == null) {
            return "";
        }
        String started = TimeFormat.clock(heat.started().toLocalTime()) + " \u2192";
        LocalDateTime end = heat.ended() != null ? heat.ended() : heat.lastMessage();
        return end == null ? started : started + " " + TimeFormat.clock(end.toLocalTime());
    }
}
