package org.lisasp.alphatiming.ui.common;

import java.time.LocalTime;

/** The time of day to show for a time of day the timekeeping system sent: the transmitted value plus the offset. */
public final class DeviceClock {

    private static final long HOUR_MILLIS = 3_600_000;
    private static final long DAY_MILLIS = 24 * HOUR_MILLIS;

    private DeviceClock() {
    }

    public static LocalTime timeOfDay(int transmittedMillis, int offsetHours) {
        return LocalTime.ofNanoOfDay(Math.floorMod(transmittedMillis + offsetHours * HOUR_MILLIS, DAY_MILLIS) * 1_000_000);
    }
}
