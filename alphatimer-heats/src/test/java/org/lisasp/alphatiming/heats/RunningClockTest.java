package org.lisasp.alphatiming.heats;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunningClockTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 5, 1, 10, 0, 0);

    private static HeatDto heat(HeatStatus status, LocalDateTime started, LocalDateTime ended) {
        return new HeatDto(1, 1, status, started, ended, 2, List.of());
    }

    @Test
    void openHeatHasNoTime() {
        assertThat(RunningClock.millis(heat(HeatStatus.Open, null, null), START)).isZero();
        assertThat(RunningClock.millis(null, START)).isZero();
    }

    @Test
    void startedHeatRunsWithTheClock() {
        assertThat(RunningClock.millis(heat(HeatStatus.Started, START, null), START.plusNanos(5_500_000_000L))).isEqualTo(5500);
    }

    @Test
    void finishedHeatIsFrozen() {
        HeatDto h = heat(HeatStatus.Finished, START, START.plusSeconds(20));
        assertThat(RunningClock.millis(h, START.plusSeconds(500))).isEqualTo(20_000);
    }

    @Test
    void startedHeatWithEndIsFrozenToo() {
        HeatDto h = heat(HeatStatus.Started, START, START.plusSeconds(20));
        assertThat(RunningClock.millis(h, START.plusSeconds(500))).isEqualTo(20_000);
    }

    @Test
    void neverNegative() {
        assertThat(RunningClock.millis(heat(HeatStatus.Started, START, null), START.minusSeconds(3))).isZero();
    }

    private static HeatDto started(LocalDateTime started, LocalDateTime lastMessage) {
        return new HeatDto(1, 1, HeatStatus.Started, started, null, 2, List.of(), lastMessage);
    }

    @Test
    void aHeatWhoseLastMessageIsRecentKeepsRunningWhileRecording() {
        HeatDto heat = started(START, START.plusSeconds(12));
        assertThat(RunningClock.millis(heat, START.plusSeconds(14), true)).isEqualTo(14_000);
    }

    @Test
    void theClockStandsStillAtTheLastMessageWhenNothingIsRecording() {
        HeatDto heat = started(START, START.plusSeconds(25));
        assertThat(RunningClock.millis(heat, START.plusSeconds(2 * 3600), false)).isEqualTo(25_000);
    }

    @Test
    void theClockStandsStillAtTheLastMessageWhenNoMessageArrivedForFiveMinutes() {
        HeatDto heat = started(START, START.plusSeconds(25));
        assertThat(RunningClock.millis(heat, START.plusSeconds(25 + 299), true)).isEqualTo(25_000 + 299_000);
        assertThat(RunningClock.millis(heat, START.plusSeconds(25 + 301), true)).isEqualTo(25_000);
        assertThat(RunningClock.millis(heat, START.plusSeconds(2 * 3600), true)).isEqualTo(25_000);
    }

    @Test
    void aHeatWithoutAKnownLastMessageDoesNotRunOnFromItsStart() {
        assertThat(RunningClock.millis(started(START, null), START.plusSeconds(2 * 3600), false)).isZero();
        assertThat(RunningClock.millis(started(START, null), START.plusSeconds(2 * 3600), true)).isZero();
    }

    @Test
    void anEndedHeatIsFrozenAtItsEndWhateverElse() {
        HeatDto heat = new HeatDto(1, 1, HeatStatus.Finished, START, START.plusSeconds(20), 2, List.of(), START.plusSeconds(20));
        assertThat(RunningClock.millis(heat, START.plusSeconds(2 * 3600), true)).isEqualTo(20_000);
    }
}
