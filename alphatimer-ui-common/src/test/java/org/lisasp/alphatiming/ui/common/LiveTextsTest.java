package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LiveTextsTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 5, 1, 10, 0, 0);

    private Locale original;

    @BeforeEach
    void english() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    private static HeatDto started() {
        return new HeatDto(3, 2, HeatStatus.Started, START, null, 0, List.of(), START.plusSeconds(60));
    }

    @Test
    void theTitleNamesEventAndHeatOrSaysThatThereIsNoHeat() {
        assertThat(LiveTexts.title(started())).isEqualTo("Event 3 · Heat 2");
        assertThat(LiveTexts.title(null)).isEqualTo(I18n.get("live.none"));
    }

    @Test
    void theClockShowsTheRunningTimeOfTheHeat() {
        assertThat(LiveTexts.clock(started(), START.plusSeconds(65).plusNanos(500_000_000), true)).isEqualTo("1:05.5");
        assertThat(LiveTexts.clock(started(), START.plusSeconds(65), false)).isEqualTo("1:00.0");
        assertThat(LiveTexts.clock(null, START, true)).isEqualTo("0:00.0");
    }

    @Test
    void theTimesTellWhenTheHeatStartedAndFinished() {
        HeatDto finished = new HeatDto(3, 2, HeatStatus.Finished, START, START.plusSeconds(65).plusNanos(700_000_000), 0, List.of());

        assertThat(LiveTexts.times(finished, 0)).isEqualTo("10:00:00 \u2192 10:01:05");
    }

    @Test
    void withoutAnEndTheLastMessageOfTheHeatIsShownInstead() {
        assertThat(LiveTexts.times(started(), 0)).isEqualTo("10:00:00 \u2192 10:01:00");
    }

    @Test
    void withoutEndAndLastMessageOnlyTheStartIsShown() {
        assertThat(LiveTexts.times(new HeatDto(3, 2, HeatStatus.Started, START, null, 0, List.of()), 0)).isEqualTo("10:00:00 \u2192");
    }

    @Test
    void theTimesFollowTheClockOfTheDeviceWithTheOffsetWhenTheStartCarriedItsTimeOfDay() {
        int halfPastSeven = 7 * 3_600_000 + 30 * 60_000;
        HeatDto finished = new HeatDto(3, 2, HeatStatus.Finished, START, START.plusSeconds(65).plusNanos(700_000_000), 0, List.of(), null, halfPastSeven);

        assertThat(LiveTexts.times(finished, 0)).isEqualTo("07:30:00 \u2192 07:31:05");
        assertThat(LiveTexts.times(finished, 1)).isEqualTo("08:30:00 \u2192 08:31:05");
        assertThat(LiveTexts.times(finished, -8)).isEqualTo("23:30:00 \u2192 23:31:05");
    }

    @Test
    void aHeatWithoutStartHasNoTimes() {
        assertThat(LiveTexts.times(new HeatDto(3, 2, HeatStatus.Open, null, null, 0, List.of()), 0)).isEmpty();
        assertThat(LiveTexts.times(null, 0)).isEmpty();
    }
}
