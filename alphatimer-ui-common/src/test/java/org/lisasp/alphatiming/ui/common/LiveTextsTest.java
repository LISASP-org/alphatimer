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
        return new HeatDto("", 3, 2, HeatStatus.Started, START, null, 0, List.of(), START.plusSeconds(60));
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
        HeatDto finished = new HeatDto("", 3, 2, HeatStatus.Finished, START, START.plusSeconds(65).plusNanos(700_000_000), 0, List.of());

        assertThat(LiveTexts.times(finished)).isEqualTo("10:00:00 \u2192 10:01:05");
    }

    @Test
    void withoutAnEndTheLastMessageOfTheHeatIsShownInstead() {
        assertThat(LiveTexts.times(started())).isEqualTo("10:00:00 \u2192 10:01:00");
    }

    @Test
    void withoutEndAndLastMessageOnlyTheStartIsShown() {
        assertThat(LiveTexts.times(new HeatDto("", 3, 2, HeatStatus.Started, START, null, 0, List.of()))).isEqualTo("10:00:00 \u2192");
    }

    @Test
    void aHeatWithoutStartHasNoTimes() {
        assertThat(LiveTexts.times(new HeatDto("", 3, 2, HeatStatus.Open, null, null, 0, List.of()))).isEmpty();
        assertThat(LiveTexts.times(null)).isEmpty();
    }
}
