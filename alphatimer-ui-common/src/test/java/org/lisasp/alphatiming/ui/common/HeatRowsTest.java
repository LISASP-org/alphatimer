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

class HeatRowsTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 5, 1, 10, 0, 0);

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

    private static HeatDto heat(int event, int heat, LocalDateTime started, LocalDateTime ended) {
        return new HeatDto(event, heat, HeatStatus.Started, started, ended, 0, List.of(), started);
    }

    @Test
    void aRowShowsStartFinishAndTheHeat() {
        List<HeatRows.Row> rows = HeatRows.from(List.of(heat(3, 2, TEN, TEN.plusSeconds(65))), 0);

        assertThat(rows).containsExactly(new HeatRows.Row("10:00:00", "10:01:05", "03-002"));
    }

    @Test
    void anUnfinishedHeatHasNoFinish() {
        assertThat(HeatRows.from(List.of(heat(3, 2, TEN, null)), 0)).containsExactly(new HeatRows.Row("10:00:00", "", "03-002"));
    }

    @Test
    void anEstimatedFinishIsMarked() {
        HeatDto estimated = new HeatDto(3, 2, HeatStatus.FinishedEstimated, TEN, TEN.plusSeconds(65), 0, List.of());

        assertThat(HeatRows.from(List.of(estimated), 0)).containsExactly(new HeatRows.Row("10:00:00", "\u224810:01:05", "03-002"));
    }

    @Test
    void aHeatWithoutStartIsNotListed() {
        assertThat(HeatRows.from(List.of(heat(3, 2, null, null), heat(3, 1, TEN, null)), 0)).containsExactly(new HeatRows.Row("10:00:00", "", "03-001"));
    }

    @Test
    void theTimesFollowTheClockOfTheDeviceWithTheOffset() {
        int halfPastSeven = 7 * 3_600_000 + 30 * 60_000;
        HeatDto heat = new HeatDto(3, 2, HeatStatus.Finished, TEN, TEN.plusSeconds(65), 0, List.of(), null, halfPastSeven);

        assertThat(HeatRows.from(List.of(heat), 1)).containsExactly(new HeatRows.Row("08:30:00", "08:31:05", "03-002"));
    }

    @Test
    void theNewestHeatComesFirstByStartThenFinishThenHeatWithAnEmptyFinishAsTheNewest() {
        List<HeatDto> heats = List.of(heat(2, 1, TEN.plusMinutes(7), null),
                                      heat(1, 3, TEN.plusMinutes(5), TEN.plusMinutes(6)),
                                      heat(1, 1, TEN, null),
                                      heat(1, 4, TEN.plusMinutes(7), null),
                                      heat(1, 2, TEN, TEN.plusMinutes(1)),
                                      heat(1, 5, TEN.plusMinutes(5), TEN.plusMinutes(6)));

        assertThat(HeatRows.from(heats, 0)).extracting(HeatRows.Row::heat)
                                           .containsExactly("02-001",
                                                            "01-004",
                                                            "01-005",
                                                            "01-003",
                                                            "01-001",
                                                            "01-002");
    }

    @Test
    void numbersTooLongForTheirPlacesAreShownInFull() {
        assertThat(HeatRows.from(List.of(heat(123, 4567, TEN, null)), 0)).extracting(HeatRows.Row::heat).containsExactly("123-4567");
    }

    @Test
    void anEventSortsByItsNumberNotByItsText() {
        List<HeatDto> heats = List.of(heat(10, 1, TEN, null), heat(9, 1, TEN, null), heat(1, 10, TEN, null), heat(1, 9, TEN, null));

        assertThat(HeatRows.from(heats, 0)).extracting(HeatRows.Row::heat)
                                           .containsExactly("10-001", "09-001", "01-010", "01-009");
    }
}
