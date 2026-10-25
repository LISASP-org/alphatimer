package org.lisasp.alphatiming.heats;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.lisasp.alphatiming.heats.TestMessageCreator.T0;

class HeatTest {

    private static HeatDto emptyHeat() {
        return new HeatDto(1, 1, HeatStatus.Open, null, null, 0,
                           List.of(1, 2, 3, 4, 5, 6, 7, 8).stream().map(n -> new LaneDto(n, 0, 0, LaneStatus.NotUsed, Penalty.None, 0, false)).toList());
    }

    private static Heat prepared() {
        Heat heat = new Heat(emptyHeat());
        heat.usedLanes(TestMessageCreator.used(1, 1, "1110000000"));
        heat.start(TestMessageCreator.start(1, 1, T0));
        return heat;
    }

    private static LaneDto lane(Heat heat, int number) {
        return heat.createDto().lanes().get(number - 1);
    }

    @Test
    void usedLanesAndStart() {
        Heat heat = prepared();
        HeatDto dto = heat.createDto();
        assertThat(dto.status()).isEqualTo(HeatStatus.Started);
        assertThat(dto.started()).isEqualTo(T0);
        assertThat(dto.lapCount()).isEqualTo(2);
        assertThat(dto.lanes().stream().filter(l -> l.status() == LaneStatus.Used).map(LaneDto::number)).containsExactly(1, 2, 3);
    }

    @Test
    void splitSetsLatestTimeAndLapWithoutFinishing() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.split(1, 1, 2, 1, 1234));
        assertThat(lane(heat, 2)).isEqualTo(new LaneDto(2, 1234, 1, LaneStatus.Used, Penalty.None, 0, false));
        heat.touch(TestMessageCreator.split(1, 1, 2, 2, 2500));
        assertThat(lane(heat, 2).timeInMillis()).isEqualTo(2500);
    }

    @Test
    void finalTimeReplacesIntermediateAndMarksFinished() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.split(1, 1, 1, 1, 1000));
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        assertThat(lane(heat, 1)).isEqualTo(new LaneDto(1, 2000, 2, LaneStatus.Used, Penalty.None, 1, true));
    }

    @Test
    void placesAreComputedFromFinalTimesWithTiesSharingAPlace() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 1950));
        heat.touch(TestMessageCreator.fin(1, 1, 3, 0, 1950));
        assertThat(lane(heat, 1).place()).isEqualTo(3);
        assertThat(lane(heat, 2).place()).isEqualTo(1);
        assertThat(lane(heat, 3).place()).isEqualTo(1);
        heat.finish(TestMessageCreator.end(1, 1, T0.plusSeconds(30)));
        assertThat(heat.createDto().status()).isEqualTo(HeatStatus.Finished);
        assertThat(lane(heat, 3).place()).isEqualTo(1);
    }

    @Test
    void officialEndStopsTheClockUnlessItAlreadyStoppedWhenTheLastLaneWasDone() {
        Heat running = prepared();
        running.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        running.finish(TestMessageCreator.end(1, 1, T0.plusSeconds(30)));
        assertThat(running.createDto().ended()).isEqualTo(T0.plusSeconds(30));

        Heat done = prepared();
        done.touch(TestMessageCreator.finAt(1, 1, 1, 25_000, T0.plus(java.time.Duration.ofMillis(25_500))));
        done.touch(TestMessageCreator.finAt(1, 1, 2, 26_000, T0.plus(java.time.Duration.ofMillis(26_500))));
        done.touch(TestMessageCreator.finAt(1, 1, 3, 27_000, T0.plus(java.time.Duration.ofMillis(27_500))));
        LocalDateTime frozen = done.createDto().ended();
        done.finish(TestMessageCreator.end(1, 1, T0.plusSeconds(100)));
        assertThat(done.createDto().status()).isEqualTo(HeatStatus.Finished);
        assertThat(done.createDto().ended()).isEqualTo(frozen);
        assertThat(RunningClock.millis(done.createDto(), T0.plusSeconds(200))).isEqualTo(27_000);
    }

    @Test
    void deviceRanksAreIgnoredBecauseTheyAreProvisionalOrInconsistent() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.splitWithRank(1, 1, 1, 1, 6, 1000));
        assertThat(lane(heat, 1).place()).isZero();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 2, 2000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 1, 2100));
        heat.finish(TestMessageCreator.end(1, 1, T0.plusSeconds(30)));
        assertThat(lane(heat, 1).place()).isEqualTo(1);
        assertThat(lane(heat, 2).place()).isEqualTo(2);
    }

    @Test
    void placesUpdateWhenALaterLaneFinishesFaster() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        assertThat(lane(heat, 1).place()).isEqualTo(1);
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 1900));
        assertThat(lane(heat, 2).place()).isEqualTo(1);
        assertThat(lane(heat, 1).place()).isEqualTo(2);
    }

    @Test
    void restartingTheHeatRestartsTheClock() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        heat.start(TestMessageCreator.start(1, 1, T0.plusSeconds(60)));
        assertThat(heat.createDto().started()).isEqualTo(T0.plusSeconds(60));
        assertThat(heat.createDto().ended()).isNull();
        assertThat(heat.createDto().status()).isEqualTo(HeatStatus.Started);
    }

    private static Heat finishedHeat() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.finAt(1, 1, 1, 25_000, T0.plus(java.time.Duration.ofMillis(25_500))));
        heat.didNotStart(TestMessageCreator.dns(1, 1, 2));
        heat.finish(TestMessageCreator.end(1, 1, T0.plusSeconds(30)));
        return heat;
    }

    @Test
    void aRepeatedStartWithTheResultsKeepsAFinishedHeat() {
        Heat heat = finishedHeat();
        HeatDto before = heat.createDto();

        heat.start(TestMessageCreator.start(1, 1, T0.plusSeconds(120), RefinedMessageType.CurrentRaceResult, RefinedTimeType.Normal));

        assertThat(heat.createDto()).isEqualTo(before);
    }

    @Test
    void aCorrectedStartKeepsAFinishedHeat() {
        Heat heat = finishedHeat();
        HeatDto before = heat.createDto();

        heat.start(TestMessageCreator.start(1, 1, T0.plusSeconds(120), RefinedMessageType.Live, RefinedTimeType.CorrectedTime));

        assertThat(heat.createDto()).isEqualTo(before);
    }

    @Test
    void aManualLiveStartRestartsTheHeat() {
        Heat heat = finishedHeat();

        heat.start(TestMessageCreator.start(1, 1, T0.plusSeconds(120), RefinedMessageType.Live, RefinedTimeType.ManualTime));

        HeatDto dto = heat.createDto();
        assertThat(dto.started()).isEqualTo(T0.plusSeconds(120));
        assertThat(dto.ended()).isNull();
        assertThat(lane(heat, 1).timeInMillis()).isZero();
        assertThat(lane(heat, 2).penalty()).isEqualTo(Penalty.None);
    }

    @Test
    void aRepeatedStartStartsAHeatThatHasNotStartedYet() {
        Heat heat = new Heat(emptyHeat());

        heat.start(TestMessageCreator.start(1, 1, T0, RefinedMessageType.CurrentRaceResult, RefinedTimeType.Normal));

        assertThat(heat.createDto().status()).isEqualTo(HeatStatus.Started);
        assertThat(heat.createDto().started()).isEqualTo(T0);
    }

    @Test
    void clockStopsWhenEveryUsedLaneIsDoneEvenWithoutOfficialEnd() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        heat.didNotStart(TestMessageCreator.dns(1, 1, 2));
        assertThat(heat.createDto().ended()).isNull();
        heat.touch(TestMessageCreator.fin(1, 1, 3, 0, 2100));
        assertThat(heat.createDto().ended()).isEqualTo(T0.plusSeconds(1));
        assertThat(heat.createDto().status()).isEqualTo(HeatStatus.Started);
    }

    @Test
    void penaltyLanesAndUnfinishedLanesGetNoPlace() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        heat.didNotStart(TestMessageCreator.dns(1, 1, 2));
        heat.touch(TestMessageCreator.split(1, 1, 3, 1, 900));
        heat.didNotFinish(TestMessageCreator.dnf(1, 1, 3));
        heat.finish(TestMessageCreator.end(1, 1, T0.plusSeconds(30)));
        assertThat(lane(heat, 1).place()).isEqualTo(1);
        assertThat(lane(heat, 2)).isEqualTo(new LaneDto(2, 0, 0, LaneStatus.Used, Penalty.DidNotStart, 0, false));
        assertThat(lane(heat, 3)).isEqualTo(new LaneDto(3, 900, 1, LaneStatus.Used, Penalty.DidNotFinish, 0, false));
    }

    @Test
    void heatWithoutFinishTimesGetsNoPlaces() {
        Heat heat = prepared();
        heat.finish(TestMessageCreator.end(1, 1, T0.plusSeconds(30)));
        assertThat(heat.createDto().lanes()).allMatch(l -> l.place() == 0);
    }

    @Test
    void unknownLaneIsRejectedWithoutChangingTheHeat() {
        Heat heat = prepared();
        HeatDto before = heat.createDto();
        assertThatThrownBy(() -> heat.touch(TestMessageCreator.split(1, 1, 9, 1, 1000))).isInstanceOfSatisfying(UnknownLaneException.class, ex -> {
            assertThat(ex.event()).isEqualTo(1);
            assertThat(ex.heat()).isEqualTo(1);
            assertThat(ex.lane()).isEqualTo(9);
        });
        assertThat(heat.createDto()).isEqualTo(before);
    }

    @Test
    void zeroTimeIsIgnoredAndNotUsedLanesStayEmpty() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.split(1, 1, 1, 1, 0));
        assertThat(lane(heat, 1).timeInMillis()).isZero();
        heat.touch(TestMessageCreator.split(1, 1, 5, 1, 1000));
        assertThat(lane(heat, 5)).isEqualTo(new LaneDto(5, 0, 0, LaneStatus.NotUsed, Penalty.None, 0, false));
    }

    @Test
    void startResetsTimesAndPlaces() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 1, 2000));
        heat.start(TestMessageCreator.start(1, 1, T0.plusSeconds(60)));
        assertThat(lane(heat, 1)).isEqualTo(new LaneDto(1, 0, 0, LaneStatus.Used, Penalty.None, 0, false));
    }

    @Test
    void everyLiveTimeSyncsTheStartOfTheHeatToTheDeviceClock() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.splitAt(1, 1, 1, 1, 12_340, T0.plusSeconds(13)));
        assertThat(heat.createDto().started()).isEqualTo(T0.plus(java.time.Duration.ofMillis(660)));

        heat.touch(TestMessageCreator.splitAt(1, 1, 1, 2, 25_000, T0.plus(java.time.Duration.ofMillis(25_500))));
        assertThat(heat.createDto().started()).as("the latest message is the best estimate").isEqualTo(T0.plus(java.time.Duration.ofMillis(500)));
    }

    @Test
    void theRunningTimeOfAHeatWhoseLanesAreAllDoneEqualsTheFinalTimeOfTheLastLane() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.finAt(1, 1, 1, 25_000, T0.plus(java.time.Duration.ofMillis(25_500))));
        heat.touch(TestMessageCreator.finAt(1, 1, 2, 26_000, T0.plus(java.time.Duration.ofMillis(26_500))));
        heat.touch(TestMessageCreator.finAt(1, 1, 3, 27_000, T0.plus(java.time.Duration.ofMillis(27_500))));
        HeatDto dto = heat.createDto();
        assertThat(dto.ended()).isNotNull();
        assertThat(RunningClock.millis(dto, T0.plusSeconds(100))).isEqualTo(27_000);
    }

    @Test
    void repeatedResultsAndTimesAfterTheEndDoNotMoveTheStart() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.finAt(1, 1, 1, 25_000, T0.plus(java.time.Duration.ofMillis(25_500))));
        LocalDateTime synced = heat.createDto().started();

        heat.touch(TestMessageCreator.resultAt(1, 1, 1, 25_000, T0.plusSeconds(120)));
        assertThat(heat.createDto().started()).isEqualTo(synced);

        heat.finish(TestMessageCreator.end(1, 1, T0.plusSeconds(30)));
        heat.touch(TestMessageCreator.splitAt(1, 1, 1, 1, 12_000, T0.plusSeconds(200)));
        assertThat(heat.createDto().started()).isEqualTo(synced);
        assertThat(lane(heat, 1).timeInMillis()).isEqualTo(25_000);
    }

    @Test
    void aSplitAfterTheFinishKeepsTheFinalTimeAndThePlaces() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 25_000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 26_000));

        heat.touch(TestMessageCreator.split(1, 1, 1, 1, 12_000));

        assertThat(lane(heat, 1)).isEqualTo(new LaneDto(1, 25_000, 2, LaneStatus.Used, Penalty.None, 1, true));
        assertThat(lane(heat, 2).place()).isEqualTo(2);
    }

    @Test
    void aCorrectedFinishReplacesTheFinalTime() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 25_000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 24_950));

        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 24_900));

        assertThat(lane(heat, 1)).isEqualTo(new LaneDto(1, 24_900, 2, LaneStatus.Used, Penalty.None, 1, true));
        assertThat(lane(heat, 2).place()).isEqualTo(2);
    }

    @Test
    void timesWithoutAValueDoNotSync() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.splitAt(1, 1, 1, 1, 0, T0.plusSeconds(13)));
        assertThat(heat.createDto().started()).isEqualTo(T0);
    }

    @Test
    void aLiveTimeBeforeAnyStartMessageStillStartsTheClockAtTheRightMoment() {
        Heat heat = new Heat(emptyHeat());
        heat.usedLanes(TestMessageCreator.used(1, 1, "1100000000"));
        heat.touch(TestMessageCreator.splitAt(1, 1, 1, 1, 5_000, T0.plusSeconds(5)));
        assertThat(heat.createDto().status()).isEqualTo(HeatStatus.Started);
        assertThat(heat.createDto().started()).isEqualTo(T0);
    }

    @Test
    void everyAppliedMessageRemembersItsArrivalTime() {
        Heat heat = prepared();
        assertThat(heat.createDto().lastMessage()).isEqualTo(T0);
        heat.touch(TestMessageCreator.splitAt(1, 1, 1, 1, 12_340, T0.plusSeconds(13)));
        assertThat(heat.createDto().lastMessage()).isEqualTo(T0.plusSeconds(13));
        heat.didNotStart(TestMessageCreator.dns(1, 1, 2));
        assertThat(heat.createDto().lastMessage()).isEqualTo(T0);
        heat.finish(TestMessageCreator.end(1, 1, T0.plusSeconds(30)));
        assertThat(heat.createDto().lastMessage()).isEqualTo(T0.plusSeconds(30));
    }

    @Test
    void aDeletedFinalTimeClearsTheLaneUntilTheNextFinish() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 25_000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 26_000));

        heat.touch(TestMessageCreator.deletedAt(1, 1, 1, 2, 25_000, T0.plusSeconds(40)));

        assertThat(lane(heat, 1)).isEqualTo(new LaneDto(1, 0, 0, LaneStatus.Used, Penalty.None, 0, false));
        assertThat(lane(heat, 2).place()).isEqualTo(1);

        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 27_000));
        assertThat(lane(heat, 1)).isEqualTo(new LaneDto(1, 27_000, 2, LaneStatus.Used, Penalty.None, 2, true));
    }

    @Test
    void aDeletedSplitClearsTheLane() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.split(1, 1, 1, 1, 12_000));

        heat.touch(TestMessageCreator.deletedAt(1, 1, 1, 1, 12_000, T0.plusSeconds(40)));

        assertThat(lane(heat, 1)).isEqualTo(new LaneDto(1, 0, 0, LaneStatus.Used, Penalty.None, 0, false));
    }

    @Test
    void aDeletedTimeThatIsNotTheCurrentTimeOfTheLaneChangesNothing() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.split(1, 1, 1, 2, 25_000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 26_000));

        heat.touch(TestMessageCreator.deletedAt(1, 1, 1, 1, 12_000, T0.plusSeconds(40)));
        heat.touch(TestMessageCreator.deletedAt(1, 1, 2, 2, 25_000, T0.plusSeconds(41)));

        assertThat(lane(heat, 1)).isEqualTo(new LaneDto(1, 25_000, 2, LaneStatus.Used, Penalty.None, 0, false));
        assertThat(lane(heat, 2)).isEqualTo(new LaneDto(2, 26_000, 2, LaneStatus.Used, Penalty.None, 1, true));
    }

    @Test
    void aDeletedTimeDoesNotMoveTheStart() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.splitAt(1, 1, 1, 1, 12_000, T0.plusSeconds(12)));

        heat.touch(TestMessageCreator.deletedAt(1, 1, 1, 1, 12_000, T0.plusSeconds(40)));

        assertThat(heat.createDto().started()).isEqualTo(T0);
    }

    @Test
    void deletingTheFinalTimeOfTheLastLaneLetsTheClockRunAgain() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 2100));
        heat.touch(TestMessageCreator.fin(1, 1, 3, 0, 2200));
        assertThat(heat.createDto().ended()).isNotNull();

        heat.touch(TestMessageCreator.deletedAt(1, 1, 3, 2, 2200, T0.plusSeconds(40)));

        assertThat(heat.createDto().ended()).isNull();
        assertThat(heat.createDto().status()).isEqualTo(HeatStatus.Started);
    }

    @Test
    void deletingTheTimeOfALaneWithAPenaltyKeepsTheClockStopped() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 2100));
        heat.touch(TestMessageCreator.split(1, 1, 3, 1, 900));
        heat.didNotFinish(TestMessageCreator.dnf(1, 1, 3));

        heat.touch(TestMessageCreator.deletedAt(1, 1, 3, 1, 900, T0.plusSeconds(40)));

        assertThat(lane(heat, 3)).isEqualTo(new LaneDto(3, 0, 0, LaneStatus.Used, Penalty.DidNotFinish, 0, false));
        assertThat(heat.createDto().ended()).isEqualTo(T0);
    }
}
