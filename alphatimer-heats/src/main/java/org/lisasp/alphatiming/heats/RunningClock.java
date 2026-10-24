package org.lisasp.alphatiming.heats;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The running time of a heat, computed locally from the start (which is synced to the device clock). It only runs while
 * data is being recorded and while messages of the heat keep arriving; otherwise it stands still at the value of the
 * last message, so a heat that is restored from a file or that never got its end cannot count on for hours.
 */
public final class RunningClock {

    /** A race produces messages at least every few lap times; silence for this long means the heat is over or stale. */
    public static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private RunningClock() {
    }

    public static long millis(HeatDto heat, LocalDateTime now) {
        return millis(heat, now, true);
    }

    public static long millis(HeatDto heat, LocalDateTime now, boolean recording) {
        if (heat == null || heat.status() == HeatStatus.Open || heat.started() == null) {
            return 0;
        }
        if (heat.ended() != null) {
            return elapsed(heat.started(), heat.ended());
        }
        LocalDateTime last = heat.lastMessage() != null ? heat.lastMessage() : heat.started();
        if (!recording || Duration.between(last, now).compareTo(STALE_AFTER) > 0) {
            return elapsed(heat.started(), last);
        }
        return elapsed(heat.started(), now);
    }

    private static long elapsed(LocalDateTime from, LocalDateTime to) {
        return Math.max(0, Duration.between(from, to).toMillis());
    }
}
