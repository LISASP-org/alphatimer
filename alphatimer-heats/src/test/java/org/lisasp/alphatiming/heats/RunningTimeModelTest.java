package org.lisasp.alphatiming.heats;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lisasp.alphatiming.heats.TestMessageCreator.T0;

/**
 * The running time is offset + (now - reference): a start signal sets the reference to the current time with offset 0,
 * an intermediate or final time sets the reference to the current time with the time as offset.
 */
class RunningTimeModelTest {

    private static LocalDateTime at(long millis) {
        return T0.plus(Duration.ofMillis(millis));
    }

    private static Heat runningHeat() {
        HeatDto empty = new HeatDto(1, 1, HeatStatus.Open, null, null, 0,
                                    List.of(1, 2, 3, 4).stream().map(n -> new LaneDto(n, 0, 0, LaneStatus.NotUsed, Penalty.None, 0, false)).toList());
        Heat heat = new Heat(empty);
        heat.usedLanes(TestMessageCreator.used(1, 1, "1110000000"));
        return heat;
    }

    private static long clock(Heat heat, long nowMillis) {
        return RunningClock.millis(heat.createDto(), at(nowMillis));
    }

    @Test
    void aStartSignalSetsTheCurrentTimeAsReferenceWithOffsetZero() {
        Heat heat = runningHeat();
        heat.start(TestMessageCreator.start(1, 1, at(0)));

        assertThat(clock(heat, 0)).isZero();
        assertThat(clock(heat, 3_000)).isEqualTo(3_000);
        assertThat(clock(heat, 10_500)).isEqualTo(10_500);
    }

    @Test
    void anIntermediateTimeSetsTheCurrentTimeAsReferenceAndTheTimeAsOffset() {
        Heat heat = runningHeat();
        heat.start(TestMessageCreator.start(1, 1, at(0)));
        assertThat(clock(heat, 13_000)).as("before the intermediate time arrives").isEqualTo(13_000);

        heat.touch(TestMessageCreator.splitAt(1, 1, 1, 1, 12_340, at(13_000)));

        assertThat(clock(heat, 13_000)).as("at the moment it arrives the clock shows the intermediate time").isEqualTo(12_340);
        assertThat(clock(heat, 14_000)).isEqualTo(13_340);
    }

    @Test
    void aFinalTimeDoesTheSameWhileOtherLanesAreStillSwimming() {
        Heat heat = runningHeat();
        heat.start(TestMessageCreator.start(1, 1, at(0)));

        heat.touch(TestMessageCreator.finAt(1, 1, 1, 25_010, at(25_500)));

        assertThat(clock(heat, 25_500)).isEqualTo(25_010);
        assertThat(clock(heat, 26_500)).isEqualTo(26_010);
    }

    @Test
    void eachFurtherTimeSetsANewReference() {
        Heat heat = runningHeat();
        heat.start(TestMessageCreator.start(1, 1, at(0)));
        heat.touch(TestMessageCreator.splitAt(1, 1, 1, 1, 12_340, at(13_000)));
        heat.touch(TestMessageCreator.splitAt(1, 1, 2, 1, 12_900, at(13_600)));

        assertThat(clock(heat, 13_600)).isEqualTo(12_900);
        assertThat(clock(heat, 15_600)).isEqualTo(14_900);
    }

    @Test
    void aNewStartSignalSetsTheReferenceAgainWithOffsetZero() {
        Heat heat = runningHeat();
        heat.start(TestMessageCreator.start(1, 1, at(0)));
        heat.touch(TestMessageCreator.splitAt(1, 1, 1, 1, 12_340, at(13_000)));

        heat.start(TestMessageCreator.start(1, 1, at(60_000)));

        assertThat(clock(heat, 60_000)).isZero();
        assertThat(clock(heat, 62_000)).isEqualTo(2_000);
    }
}
