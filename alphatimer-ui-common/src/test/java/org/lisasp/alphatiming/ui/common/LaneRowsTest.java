package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.Penalty;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LaneRowsTest {

    private Locale original;

    @BeforeEach
    void useEnglish() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    private static HeatDto heat() {
        return new HeatDto(1, 1, HeatStatus.Started, null, null, 2, List.of(
                new LaneDto(1, 0, 0, LaneStatus.NotUsed, Penalty.None, 0, false),
                new LaneDto(2, 12_340, 1, LaneStatus.Used, Penalty.None, 0, false),
                new LaneDto(3, 25_000, 2, LaneStatus.Used, Penalty.None, 1, true),
                new LaneDto(4, 0, 0, LaneStatus.Used, Penalty.DidNotStart, 0, false),
                new LaneDto(5, 9_000, 1, LaneStatus.Used, Penalty.DidNotFinish, 0, false),
                new LaneDto(6, 0, 0, LaneStatus.Used, Penalty.None, 0, false)));
    }

    @Test
    void showsUsedLanesOnlyAndNothingWithoutAHeat() {
        assertThat(LaneRows.from(null, java.util.Set.of())).isEmpty();
        List<LaneRows.Row> rows = LaneRows.from(heat(), java.util.Set.of());
        assertThat(rows).extracting(LaneRows.Row::lane).containsExactly(2, 3, 4, 5, 6);
    }

    @Test
    void lanesUsedBeforeKeepAnEmptyRowAndNeverUsedLanesStayHidden() {
        HeatDto heat = new HeatDto(1, 2, HeatStatus.Started, null, null, 2, List.of(
                new LaneDto(1, 0, 0, LaneStatus.NotUsed, Penalty.None, 0, false),
                new LaneDto(2, 12_340, 1, LaneStatus.Used, Penalty.None, 0, false),
                new LaneDto(3, 0, 0, LaneStatus.NotUsed, Penalty.None, 0, false),
                new LaneDto(4, 0, 0, LaneStatus.Used, Penalty.None, 0, false)));
        List<LaneRows.Row> rows = LaneRows.from(heat, java.util.Set.of(1, 2, 4));
        assertThat(rows).extracting(LaneRows.Row::lane).containsExactly(1, 2, 4);
        assertThat(rows.get(0)).isEqualTo(new LaneRows.Row(1, "", "", 0, false));
        assertThat(rows.get(1).time()).isEqualTo("12.34");
    }

    @Test
    void intermediateAndFinalTimesShareOneField() {
        List<LaneRows.Row> rows = LaneRows.from(heat(), java.util.Set.of());
        assertThat(rows.get(0).time()).isEqualTo("12.34");
        assertThat(rows.get(0).detail()).isEqualTo("Lap 1");
        assertThat(rows.get(1).time()).isEqualTo("25.00");
        assertThat(rows.get(1).detail()).isEmpty();
        assertThat(rows.get(1).finished()).isTrue();
    }

    @Test
    void penaltiesAndPlaces() {
        List<LaneRows.Row> rows = LaneRows.from(heat(), java.util.Set.of());
        assertThat(rows.get(1).place()).isEqualTo(1);
        assertThat(rows.get(0).place()).isZero();
        assertThat(rows.get(2).detail()).isEqualTo("DNS");
        assertThat(rows.get(3).detail()).isEqualTo("DNF");
        assertThat(rows.get(3).time()).isEqualTo("9.00");
        assertThat(rows.get(4).time()).isEmpty();
    }

    @Test
    void germanTextsAndDecimalComma() {
        Locale.setDefault(Locale.GERMANY);
        List<LaneRows.Row> rows = LaneRows.from(heat(), java.util.Set.of());
        assertThat(rows.get(0).time()).isEqualTo("12,34");
        assertThat(rows.get(0).detail()).isEqualTo("Zwischenzeit 1");
        assertThat(rows.get(2).detail()).isEqualTo("n.a.");
        assertThat(rows.get(3).detail()).isEqualTo("aufg.");
    }
}
