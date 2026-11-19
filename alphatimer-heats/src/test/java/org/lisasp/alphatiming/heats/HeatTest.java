package org.lisasp.alphatiming.heats;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.lisasp.alphatiming.heats.TestMessageCreator.T0;

class HeatTest {

    private static HeatDto emptyHeat() {
        return new HeatDto(1, 1, HeatStatus.Open, 0, null, null, null, null, null, List.of(1, 2, 3, 4, 5, 6, 7, 8).stream().map(n -> TestLanes.lane(n, LaneStatus.NotUsed, Set.of(), 0)).toList());
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
    void eachStartKeepsTheTimeOfDayTheDeviceSent() {
        Heat heat = new Heat(emptyHeat());

        heat.start(TestMessageCreator.startAtTimeOfDay(1, 1, T0, 36_000_000));
        assertThat(heat.createDto().startTimeOfDayMillis()).isEqualTo(36_000_000);

        heat.start(TestMessageCreator.startAtTimeOfDay(1, 1, T0.plusSeconds(60), 36_060_000));
        assertThat(heat.createDto().startTimeOfDayMillis()).isEqualTo(36_060_000);
    }

    @Test
    void aStartWithoutTimeOfDayLeavesItUnknown() {
        Heat heat = new Heat(emptyHeat());
        heat.start(TestMessageCreator.startAtTimeOfDay(1, 1, T0, 36_000_000));

        heat.start(TestMessageCreator.start(1, 1, T0.plusSeconds(60)));

        assertThat(heat.createDto().startTimeOfDayMillis()).isNull();
    }

    @Test
    void splitSetsLatestTimeAndLapWithoutFinishing() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.split(1, 1, 2, 1, 1234));
        assertThat(lane(heat, 2)).isEqualTo(TestLanes.lane(2, LaneStatus.Used, Set.of(), 0, TestLanes.split(1, 1234)));
        heat.touch(TestMessageCreator.split(1, 1, 2, 2, 2500));
        assertThat(lane(heat, 2).current().orElseThrow().timeInMillis()).isEqualTo(2500);
    }

    @Test
    void finalTimeReplacesIntermediateAndMarksFinished() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.split(1, 1, 1, 1, 1000));
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        assertThat(lane(heat, 1).times()).containsExactly(TestLanes.split(1, 1000), TestLanes.finish(2, 2000));
        assertThat(lane(heat, 1).current().orElseThrow()).isEqualTo(TestLanes.finish(2, 2000));
        assertThat(lane(heat, 1).place()).isEqualTo(1);
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
        assertThat(lane(heat, 1).current()).isEmpty();
        assertThat(lane(heat, 2).penalties()).isEmpty();
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
        assertThat(lane(heat, 2)).isEqualTo(TestLanes.lane(2, LaneStatus.Used, Set.of(Penalty.DidNotStart), 0));
        assertThat(lane(heat, 3)).isEqualTo(TestLanes.lane(3, LaneStatus.Used, Set.of(Penalty.DidNotFinish), 0, TestLanes.split(1, 900)));
    }

    @Test
    void aDisqualifiedLaneKeepsItsTimeButGetsNoPlace() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 2100));
        heat.touch(TestMessageCreator.fin(1, 1, 3, 0, 2200));

        heat.disqualified(TestMessageCreator.dsq(1, 1, 1));

        assertThat(lane(heat, 1)).isEqualTo(TestLanes.lane(1, LaneStatus.Used, Set.of(Penalty.Disqualified), 0, TestLanes.finish(2, 2000)));
        assertThat(lane(heat, 2).place()).isEqualTo(1);
        assertThat(lane(heat, 3).place()).isEqualTo(2);
    }

    @Test
    void aLaneCanCarrySeveralPenalties() {
        Heat heat = prepared();

        heat.didNotFinish(TestMessageCreator.dnf(1, 1, 1));
        heat.disqualified(TestMessageCreator.dsq(1, 1, 1));

        assertThat(lane(heat, 1).penalties()).containsExactlyInAnyOrder(Penalty.DidNotFinish, Penalty.Disqualified);
    }

    @Test
    void aPenaltyKeepsTheTimesOfTheLane() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 61_230));

        heat.didNotStart(TestMessageCreator.dns(1, 1, 1));

        assertThat(lane(heat, 1)).isEqualTo(TestLanes.lane(1, LaneStatus.Used, Set.of(Penalty.DidNotStart), 0, TestLanes.finish(2, 61_230)));
    }

    @Test
    void onlyALaneWithoutPenaltyGetsAPlace() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 2100));
        heat.touch(TestMessageCreator.fin(1, 1, 3, 0, 2200));

        heat.didNotFinish(TestMessageCreator.dnf(1, 1, 1));
        heat.disqualified(TestMessageCreator.dsq(1, 1, 2));

        assertThat(lane(heat, 1).place()).isZero();
        assertThat(lane(heat, 2).place()).isZero();
        assertThat(lane(heat, 3).place()).isEqualTo(1);
    }

    @Test
    void aLiveStartClearsThePenalties() {
        Heat heat = prepared();
        heat.didNotStart(TestMessageCreator.dns(1, 1, 1));
        heat.disqualified(TestMessageCreator.dsq(1, 1, 1));

        heat.start(TestMessageCreator.start(1, 1, T0.plusSeconds(120)));

        assertThat(lane(heat, 1).penalties()).isEmpty();
    }

    @Test
    void aDisqualifiedLaneWithoutTimeIsDoneForTheClock() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 2100));

        heat.disqualified(TestMessageCreator.dsq(1, 1, 3));

        assertThat(heat.createDto().ended()).isEqualTo(T0.plusSeconds(60));
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
        assertThat(lane(heat, 1).current()).isEmpty();
        heat.touch(TestMessageCreator.split(1, 1, 5, 1, 1000));
        assertThat(lane(heat, 5)).isEqualTo(TestLanes.lane(5, LaneStatus.NotUsed, Set.of(), 0));
    }

    @Test
    void startResetsTimesAndPlaces() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 1, 2000));
        heat.start(TestMessageCreator.start(1, 1, T0.plusSeconds(60)));
        assertThat(lane(heat, 1)).isEqualTo(TestLanes.lane(1, LaneStatus.Used, Set.of(), 0));
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
        assertThat(lane(heat, 1).current().orElseThrow().timeInMillis()).isEqualTo(25_000);
    }

    @Test
    void aSplitAfterTheFinishKeepsTheFinalTimeAndThePlaces() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 25_000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 26_000));

        heat.touch(TestMessageCreator.split(1, 1, 1, 1, 12_000));

        assertThat(lane(heat, 1).current().orElseThrow()).isEqualTo(TestLanes.finish(2, 25_000));
        assertThat(lane(heat, 1).place()).isEqualTo(1);
        assertThat(lane(heat, 2).place()).isEqualTo(2);
    }

    @Test
    void aCorrectedFinishReplacesTheFinalTime() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 25_000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 24_950));

        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 24_900));

        assertThat(lane(heat, 1).finish().orElseThrow().timeInMillis()).isEqualTo(24_900);
        assertThat(lane(heat, 1).place()).isEqualTo(1);
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
    void aBackupTimeIsKeptBesideTheFinish() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 13_630));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 14_000));

        heat.backupTime(TestMessageCreator.backup(1, 1, 1, 2, 13_710, T0.plusSeconds(20)));

        assertThat(lane(heat, 1).finish().orElseThrow().timeInMillis()).isEqualTo(13_630);
        assertThat(lane(heat, 1).backups()).extracting(LaneTime::timeInMillis).containsExactly(13_710);
        assertThat(lane(heat, 1).place()).isEqualTo(1);
        assertThat(lane(heat, 2).place()).isEqualTo(2);
    }

    @Test
    void aBackupTimeAloneGivesNoFinish() {
        Heat heat = prepared();

        heat.backupTime(TestMessageCreator.backup(1, 1, 1, 2, 13_710, T0.plusSeconds(20)));

        assertThat(lane(heat, 1).finish()).isEmpty();
        assertThat(lane(heat, 1).backups()).hasSize(1);
        assertThat(lane(heat, 1).place()).isZero();
        assertThat(heat.createDto().ended()).isNull();
    }

    @Test
    void aRepeatedBackupTimeAddsNothingAndADeletionNeverMarksIt() {
        Heat heat = prepared();
        heat.backupTime(TestMessageCreator.backup(1, 1, 1, 2, 13_710, T0.plusSeconds(20)));
        heat.backupTime(TestMessageCreator.backup(1, 1, 1, 2, 13_710, T0.plusSeconds(21)));

        heat.touch(TestMessageCreator.deletedAt(1, 1, 1, 2, 13_710, T0.plusSeconds(40)));

        assertThat(lane(heat, 1).backups()).hasSize(1).noneMatch(LaneTime::deleted);
    }

    @Test
    void aDeletedFinalTimeClearsTheLaneUntilTheNextFinish() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 25_000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 26_000));

        heat.touch(TestMessageCreator.deletedAt(1, 1, 1, 2, 25_000, T0.plusSeconds(40)));

        assertThat(lane(heat, 1).current()).isEmpty();
        assertThat(lane(heat, 1).place()).isZero();
        assertThat(lane(heat, 2).place()).isEqualTo(1);

        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 27_000));
        assertThat(lane(heat, 1).finish().orElseThrow().timeInMillis()).isEqualTo(27_000);
        assertThat(lane(heat, 1).place()).isEqualTo(2);
    }

    @Test
    void aDeletedSplitClearsTheLane() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.split(1, 1, 1, 1, 12_000));

        heat.touch(TestMessageCreator.deletedAt(1, 1, 1, 1, 12_000, T0.plusSeconds(40)));

        assertThat(lane(heat, 1).current()).isEmpty();
    }

    @Test
    void aDeletedTimeThatIsNotTheCurrentTimeOfTheLaneChangesNothing() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.split(1, 1, 1, 2, 25_000));
        heat.touch(TestMessageCreator.fin(1, 1, 2, 0, 26_000));

        heat.touch(TestMessageCreator.deletedAt(1, 1, 1, 1, 12_000, T0.plusSeconds(40)));
        heat.touch(TestMessageCreator.deletedAt(1, 1, 2, 2, 25_000, T0.plusSeconds(41)));

        assertThat(lane(heat, 1)).isEqualTo(TestLanes.lane(1, LaneStatus.Used, Set.of(), 0, TestLanes.split(2, 25_000)));
        assertThat(lane(heat, 2)).isEqualTo(TestLanes.lane(2, LaneStatus.Used, Set.of(), 1, TestLanes.finish(2, 26_000)));
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

        assertThat(lane(heat, 3).current()).isEmpty();
        assertThat(lane(heat, 3).penalties()).containsExactly(Penalty.DidNotFinish);
        assertThat(heat.createDto().ended()).isEqualTo(T0);
    }

    @Test
    void anEstimatedEndFinishesTheHeatAsEstimated() {
        Heat heat = prepared();

        heat.estimateEnd(T0.plusSeconds(60));

        assertThat(heat.createDto().status()).isEqualTo(HeatStatus.FinishedEstimated);
        assertThat(heat.createDto().ended()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void theOfficialEndReplacesTheEstimatedEnd() {
        Heat heat = prepared();
        heat.estimateEnd(T0.plusSeconds(60));

        heat.finish(TestMessageCreator.end(1, 1, T0.plusSeconds(90)));

        assertThat(heat.createDto().status()).isEqualTo(HeatStatus.Finished);
        assertThat(heat.createDto().ended()).isEqualTo(T0.plusSeconds(90));
    }

    @Test
    void aDeletedTimeKeepsTheEstimatedEnd() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 25_000));
        heat.estimateEnd(T0.plusSeconds(60));

        heat.touch(TestMessageCreator.deletedAt(1, 1, 1, 2, 25_000, T0.plusSeconds(70)));

        assertThat(lane(heat, 1).current()).isEmpty();
        assertThat(heat.createDto().status()).isEqualTo(HeatStatus.FinishedEstimated);
        assertThat(heat.createDto().ended()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void aLiveRestartClearsTheEstimatedEnd() {
        Heat heat = prepared();
        heat.estimateEnd(T0.plusSeconds(60));

        heat.start(TestMessageCreator.start(1, 1, T0.plusSeconds(120)));

        assertThat(heat.createDto().status()).isEqualTo(HeatStatus.Started);
        assertThat(heat.createDto().ended()).isNull();
    }

    @Test
    void everySplitIsKept() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.split(1, 1, 1, 1, 30_000));
        heat.touch(TestMessageCreator.split(1, 1, 1, 2, 61_000));

        assertThat(lane(heat, 1).times()).extracting(LaneTime::lap, LaneTime::kind, LaneTime::timeInMillis)
                                         .containsExactly(tuple(1, TimeKind.Split, 30_000), tuple(2, TimeKind.Split, 61_000));
    }

    @Test
    void aRepeatedTimeAddsNoEntry() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.finAt(1, 1, 1, 61_230, T0.plusSeconds(61)));
        heat.touch(TestMessageCreator.resultAt(1, 1, 1, 61_230, T0.plusSeconds(70)));

        assertThat(lane(heat, 1).times()).hasSize(1);
        LaneTime kept = lane(heat, 1).times().getFirst();
        assertThat(kept.kind()).isEqualTo(TimeKind.Finish);
        assertThat(kept.source()).isEqualTo(TimeSource.Live);
        assertThat(kept.arrived()).isEqualTo(T0.plusSeconds(61));
    }

    @Test
    void aDifferentResultTimeSupersedesTheLiveTime() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.finAt(1, 1, 1, 61_230, T0.plusSeconds(61)));
        heat.touch(TestMessageCreator.resultAt(1, 1, 1, 61_250, T0.plusSeconds(70)));

        assertThat(lane(heat, 1).times()).hasSize(2);
        assertThat(lane(heat, 1).finish().orElseThrow().timeInMillis()).isEqualTo(61_250);
        assertThat(lane(heat, 1).finish().orElseThrow().source()).isEqualTo(TimeSource.Result);
    }

    @Test
    void aDeletedSplitIsMarkedNotRemoved() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.split(1, 1, 1, 1, 30_000));
        heat.touch(TestMessageCreator.split(1, 1, 1, 2, 45_000));

        heat.touch(TestMessageCreator.deletedAt(1, 1, 1, 1, 30_000, T0.plusSeconds(40)));

        assertThat(lane(heat, 1).times()).extracting(LaneTime::timeInMillis, LaneTime::deleted)
                                         .containsExactly(tuple(30_000, true), tuple(45_000, false));
        assertThat(lane(heat, 1).current().orElseThrow().timeInMillis()).isEqualTo(45_000);
    }

    @Test
    void aDeletedFinishWithdrawsThePlaceAndTheEnd() {
        Heat heat = prepared();
        heat.usedLanes(TestMessageCreator.used(1, 1, "1000000000"));
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 25_000));
        assertThat(heat.createDto().ended()).isNotNull();
        assertThat(lane(heat, 1).place()).isEqualTo(1);

        heat.touch(TestMessageCreator.deletedAt(1, 1, 1, 2, 25_000, T0.plusSeconds(40)));

        assertThat(lane(heat, 1).finish()).isEmpty();
        assertThat(lane(heat, 1).place()).isZero();
        assertThat(heat.createDto().ended()).isNull();
        assertThat(lane(heat, 1).times()).hasSize(1);
    }

    @Test
    void aSplitAfterTheFinishDoesNotReplaceIt() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.fin(1, 1, 1, 0, 25_000));
        heat.touch(TestMessageCreator.split(1, 1, 1, 1, 12_000));

        assertThat(lane(heat, 1).current().orElseThrow().kind()).isEqualTo(TimeKind.Finish);
        assertThat(lane(heat, 1).times()).hasSize(2);
    }

    @Test
    void aLiveStartClearsTheTimes() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.split(1, 1, 1, 1, 12_000));

        heat.start(TestMessageCreator.start(1, 1, T0.plusSeconds(60)));

        assertThat(lane(heat, 1).times()).isEmpty();
    }

    @Test
    void aReadyToStartDoesNotChangeAStartedHeat() {
        Heat heat = prepared();
        HeatDto before = heat.createDto();

        heat.readyToStart(TestMessageCreator.readyToStart(1, 1, 4, T0.plusSeconds(30)));

        assertThat(heat.createDto()).isEqualTo(before);
    }

    @Test
    void sourceAndTypeOfATimeFollowTheMessage() {
        Heat heat = prepared();
        heat.touch(TestMessageCreator.finOfType(1, 1, 1, 61_000, RefinedMessageType.Live, RefinedTimeType.Normal));
        heat.touch(TestMessageCreator.finOfType(1, 1, 1, 61_100, RefinedMessageType.Live, RefinedTimeType.ManualTime));
        heat.touch(TestMessageCreator.finOfType(1, 1, 1, 61_200, RefinedMessageType.CurrentRaceResult, RefinedTimeType.CorrectedTime));

        assertThat(lane(heat, 1).times()).extracting(LaneTime::timeInMillis, LaneTime::source, LaneTime::type)
                                         .containsExactly(tuple(61_000, TimeSource.Live, TimeType.Normal),
                                                          tuple(61_100, TimeSource.Live, TimeType.Manual),
                                                          tuple(61_200, TimeSource.Result, TimeType.Corrected));
    }
}
