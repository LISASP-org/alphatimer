package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TimeFormatTest {

    @Test
    void laneTimes() {
        assertThat(TimeFormat.time(0)).isEmpty();
        assertThat(TimeFormat.time(-5)).isEmpty();
        assertThat(TimeFormat.time(5_000, Locale.ENGLISH)).isEqualTo("5.00");
        assertThat(TimeFormat.time(65_430, Locale.ENGLISH)).isEqualTo("1:05.43");
        assertThat(TimeFormat.time(65_439, Locale.ENGLISH)).isEqualTo("1:05.43");
        assertThat(TimeFormat.time(600_000, Locale.ENGLISH)).isEqualTo("10:00.00");
    }

    @Test
    void runningTimes() {
        assertThat(TimeFormat.running(0, Locale.ENGLISH)).isEqualTo("0:00.0");
        assertThat(TimeFormat.running(5_500, Locale.ENGLISH)).isEqualTo("0:05.5");
        assertThat(TimeFormat.running(3_725_000, Locale.ENGLISH)).isEqualTo("1:02:05.0");
        assertThat(TimeFormat.running(-100, Locale.ENGLISH)).isEqualTo("0:00.0");
    }

    @Test
    void germanUsesAComma() {
        assertThat(TimeFormat.time(65_430, Locale.GERMANY)).isEqualTo("1:05,43");
        assertThat(TimeFormat.time(5_000, Locale.GERMANY)).isEqualTo("5,00");
        assertThat(TimeFormat.running(3_725_000, Locale.GERMANY)).isEqualTo("1:02:05,0");
    }

    @Test
    void timeOfDayForTheMessageLog() {
        assertThat(TimeFormat.clock(LocalTime.of(9, 5, 3, 900_000_000))).isEqualTo("09:05:03");
    }
}
