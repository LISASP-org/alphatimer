package org.lisasp.alphatiming.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.TestLanes;
import org.lisasp.alphatiming.heats.LiveHeats;
import org.lisasp.alphatiming.heats.Penalty;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FinishedHeatsTest {

    private Locale original;
    private final FinishedHeats finished = new FinishedHeats();

    @BeforeEach
    void english() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    private static HeatDto heat(int event, int heat, HeatStatus status) {
        return heat(event, "", heat, status);
    }

    private static HeatDto heat(int event, String round, int heat, HeatStatus status) {
        return new HeatDto(event, round, heat, status, 0, null, null, null, null, null, List.of(
                TestLanes.lane(1, LaneStatus.Used, Set.of(), 2, TestLanes.finish(0, 65_430)),
                TestLanes.lane(2, LaneStatus.Used, Set.of(), 1, TestLanes.finish(0, 61_000)),
                TestLanes.lane(3, LaneStatus.Used, Set.of(Penalty.DidNotStart), 0),
                TestLanes.lane(4, LaneStatus.NotUsed, Set.of(), 0)));
    }

    @Test
    void aFinishedHeatIsDescribedOnceWithItsUsedLanes() {
        String text = finished.describe(new LiveHeats(heat(3, 2, HeatStatus.Finished), null)).orElseThrow();

        assertThat(text.lines().toList()).containsExactly("Event 3 · Heat 2",
                                                         "   1     1:05.43         #2",
                                                         "   2     1:01.00         #1",
                                                         "   3              DNS");
        assertThat(finished.describe(new LiveHeats(heat(3, 2, HeatStatus.Finished), null))).isEmpty();
    }

    @Test
    void runningHeatsAndNoHeatAreNotDescribed() {
        assertThat(finished.describe(new LiveHeats(heat(3, 2, HeatStatus.Started), null))).isEmpty();
        assertThat(finished.describe(LiveHeats.NONE)).isEmpty();
    }

    @Test
    void theSameHeatOfAnotherRoundIsDescribedToo() {
        finished.describe(new LiveHeats(heat(3, "PreliminaryHeat", 2, HeatStatus.Finished), null));

        assertThat(finished.describe(new LiveHeats(heat(3, "Final", 2, HeatStatus.Finished), null))).isPresent();
    }

    @Test
    void theNextFinishedHeatIsDescribedAgain() {
        finished.describe(new LiveHeats(heat(3, 2, HeatStatus.Finished), null));

        assertThat(finished.describe(new LiveHeats(heat(3, 3, HeatStatus.Finished), null))).isPresent();
    }
}
