package org.lisasp.alphatiming.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.LiveHeats;
import org.lisasp.alphatiming.heats.Penalty;

import java.util.List;
import java.util.Locale;

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
        return new HeatDto(event, heat, status, null, null, 0, List.of(
                new LaneDto(1, 65_430, 0, LaneStatus.Used, Penalty.None, 2, true),
                new LaneDto(2, 61_000, 0, LaneStatus.Used, Penalty.None, 1, true),
                new LaneDto(3, 0, 0, LaneStatus.Used, Penalty.DidNotStart, 0, false),
                new LaneDto(4, 0, 0, LaneStatus.NotUsed, Penalty.None, 0, false)));
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
    void theNextFinishedHeatIsDescribedAgain() {
        finished.describe(new LiveHeats(heat(3, 2, HeatStatus.Finished), null));

        assertThat(finished.describe(new LiveHeats(heat(3, 3, HeatStatus.Finished), null))).isPresent();
    }
}
