package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.TestLanes;
import org.lisasp.alphatiming.heats.LaneTime;
import org.lisasp.alphatiming.heats.Penalty;
import org.lisasp.alphatiming.heats.TestMessageCreator;
import org.lisasp.alphatiming.heats.TimeKind;
import org.lisasp.alphatiming.heats.TimeSource;
import org.lisasp.alphatiming.heats.TimeType;

import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        return new HeatDto(1, 1, HeatStatus.Started, 2, null, null, null, null, null, List.of(
                TestLanes.lane(1, LaneStatus.NotUsed, Set.of(), 0),
                TestLanes.lane(2, LaneStatus.Used, Set.of(), 0, TestLanes.split(1, 12_340)),
                TestLanes.lane(3, LaneStatus.Used, Set.of(), 1, TestLanes.finish(2, 25_000)),
                TestLanes.lane(4, LaneStatus.Used, Set.of(Penalty.DidNotStart), 0),
                TestLanes.lane(5, LaneStatus.Used, Set.of(Penalty.DidNotFinish), 0, TestLanes.split(1, 9_000)),
                TestLanes.lane(6, LaneStatus.Used, Set.of(), 0)));
    }

    @Test
    void showsUsedLanesOnlyAndNothingWithoutAHeat() {
        assertThat(LaneRows.from(null, java.util.Set.of())).isEmpty();
        List<LaneRows.Row> rows = LaneRows.from(heat(), java.util.Set.of());
        assertThat(rows).extracting(LaneRows.Row::lane).containsExactly(2, 3, 4, 5, 6);
    }

    @Test
    void lanesUsedBeforeKeepAnEmptyRowAndNeverUsedLanesStayHidden() {
        HeatDto heat = new HeatDto(1, 2, HeatStatus.Started, 2, null, null, null, null, null, List.of(
                TestLanes.lane(1, LaneStatus.NotUsed, Set.of(), 0),
                TestLanes.lane(2, LaneStatus.Used, Set.of(), 0, TestLanes.split(1, 12_340)),
                TestLanes.lane(3, LaneStatus.NotUsed, Set.of(), 0),
                TestLanes.lane(4, LaneStatus.Used, Set.of(), 0)));
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
    void aDeletedFinishShowsTheEarlierSplitAsNotFinished() {
        LaneTime deletedFinish = new LaneTime(2, TimeKind.Finish, 61_230, TimeSource.Live, TimeType.Normal, true, TestMessageCreator.T0);
        HeatDto heat = new HeatDto(1, 1, HeatStatus.Started, 2, null, null, null, null, null, List.of(
                new LaneDto(1, LaneStatus.Used, Set.of(), 0, List.of(TestLanes.split(1, 30_000), deletedFinish))));

        assertThat(LaneRows.from(heat, Set.of())).containsExactly(new LaneRows.Row(1, "30.00", "Lap 1", 0, false));
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
    void aDisqualifiedLaneShowsItsTimeWithoutPlace() {
        HeatDto heat = new HeatDto(1, 1, HeatStatus.Finished, 2, null, null, null, null, null, List.of(
                TestLanes.lane(1, LaneStatus.Used, Set.of(Penalty.Disqualified), 0, TestLanes.finish(2, 25_000))));

        assertThat(LaneRows.from(heat, java.util.Set.of())).containsExactly(new LaneRows.Row(1, "25.00", "DSQ", 0, true));

        Locale.setDefault(Locale.GERMANY);
        assertThat(LaneRows.from(heat, java.util.Set.of()).getFirst().detail()).isEqualTo("disq.");
    }

    @Test
    void everyPenaltyOfALaneIsShown() {
        HeatDto heat = new HeatDto(1, 1, HeatStatus.Finished, 2, null, null, null, null, null, List.of(
                TestLanes.lane(1, LaneStatus.Used, Set.of(Penalty.DidNotFinish, Penalty.Disqualified), 0, TestLanes.finish(2, 25_000))));

        assertThat(LaneRows.from(heat, java.util.Set.of()).getFirst().detail()).isEqualTo("DNF, DSQ");
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
