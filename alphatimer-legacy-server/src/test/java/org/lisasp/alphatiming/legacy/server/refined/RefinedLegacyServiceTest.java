package org.lisasp.alphatiming.legacy.server.refined;

import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeOrigin;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.refinedmessages.accepted.HeatRemovedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.StartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.LaneNumbering;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;
import org.lisasp.alphatiming.legacy.api.Heat;
import org.lisasp.alphatiming.legacy.api.LaneStatus;
import org.lisasp.alphatiming.legacy.server.InMemoryLaneTimeRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefinedLegacyServiceTest {

    private final RefinedLegacyService service = new RefinedLegacyService(new InMemoryLaneTimeRepository());

    private static TimeMessage time(int event, int heat, int lane, RefinedKindOfTime kind, RefinedMessageType type, int lap, RefinedTimeType timeType,
                                    int millis) {
        return new TimeMessage(LocalDateTime.of(2021, 6, 21, 14, 53), "WK", (short) event, (byte) heat, type, kind, (byte) lane, (byte) lap, (byte) 2,
                               (byte) 0, millis, timeType, RefinedTimeOrigin.Primary);
    }

    private static TimeMessage finish(int event, int heat, int lane, RefinedMessageType type, int millis) {
        return time(event, heat, lane, RefinedKindOfTime.Finish, type, 2, RefinedTimeType.Normal, millis);
    }

    private static TimeMessage deleted(int event, int heat, int lane, int lap, int millis) {
        return time(event, heat, lane, RefinedKindOfTime.SplitTime, RefinedMessageType.Live, lap, RefinedTimeType.DeletedTime, millis);
    }

    private static TimeMessage finishOfRound(String round, int lane, int second, int millis) {
        return new TimeMessage(LocalDateTime.of(2021, 6, 21, 14, 53, second), "WK", (short) 1, (byte) 2, round, RefinedMessageType.CurrentRaceResult,
                               RefinedKindOfTime.Finish, (byte) lane, (byte) 2, (byte) 2, (byte) 0, millis, RefinedTimeType.Normal, RefinedTimeOrigin.Primary);
    }

    private long[] times(int laneIndex) {
        return service.getHeats()[0].getLanes().get(laneIndex).getTimes();
    }

    private LaneStatus[] stati(int laneIndex) {
        return service.getHeats()[0].getLanes().get(laneIndex).getStati();
    }

    @Test
    void storesFinishTimesPerHeatAndIgnoresEverythingElse() {
        assertThat(service.getHeats()).isEmpty();

        service.accept(finish(1, 1, 2, RefinedMessageType.Live, 19_000));
        service.accept(finish(1, 1, 3, RefinedMessageType.CurrentRaceResult, 19_500));
        service.accept(finish(1, 2, 1, RefinedMessageType.Live, 20_000));
        service.accept(time(1, 1, 4, RefinedKindOfTime.SplitTime, RefinedMessageType.Live, 1, RefinedTimeType.Normal, 9_000));
        service.accept(finish(0, 1, 4, RefinedMessageType.Live, 9_000));
        service.accept(finish(1, 0, 4, RefinedMessageType.Live, 9_000));
        service.accept(new StartMessage(LocalDateTime.of(2021, 6, 21, 14, 52), "WK", (short) 1, (byte) 3, RefinedMessageType.Live, (byte) 2, (byte) 0, 0,
                                        RefinedTimeType.Normal));

        Heat[] heats = service.getHeats();
        assertThat(heats).extracting(Heat::getEvent, Heat::getHeat, Heat::getId)
                         .containsExactly(org.assertj.core.groups.Tuple.tuple(1, 1, "1"), org.assertj.core.groups.Tuple.tuple(1, 2, "2"));
        assertThat(heats[0].getLanes()).containsOnlyKeys(0, 1, 2);
        assertThat(times(1)).containsExactly(19_000);
        assertThat(times(2)).containsExactly(19_500);
    }

    @Test
    void theLaneStatusFollowsTheMessageType() {
        service.accept(finish(1, 1, 1, RefinedMessageType.Live, 19_000));
        service.accept(finish(1, 1, 1, RefinedMessageType.CurrentRaceResult, 19_010));
        service.accept(finish(1, 1, 1, RefinedMessageType.PreviousRaceResult, 19_020));

        assertThat(stati(0)).containsExactly(LaneStatus.RaceTimes, LaneStatus.ResultsOfTheRace, LaneStatus.ResultsOfThePreviousRace);
    }

    @Test
    void backupTimesAreIgnored() {
        service.accept(finish(1, 1, 2, RefinedMessageType.Live, 25_000));
        service.accept(new TimeMessage(LocalDateTime.of(2021, 6, 21, 14, 54), "WK", (short) 1, (byte) 1, RefinedMessageType.CurrentRaceResult,
                                       RefinedKindOfTime.Finish, (byte) 3, (byte) 2, (byte) 2, (byte) 0, 26_000, RefinedTimeType.Normal,
                                       RefinedTimeOrigin.Backup));
        service.accept(new TimeMessage(LocalDateTime.of(2021, 6, 21, 14, 55), "WK", (short) 1, (byte) 1, RefinedMessageType.CurrentRaceResult,
                                       RefinedKindOfTime.SplitTime, (byte) 2, (byte) 2, (byte) 2, (byte) 0, 25_000, RefinedTimeType.DeletedTime,
                                       RefinedTimeOrigin.Backup));

        assertThat(service.getHeats()[0].getLanes()).containsOnlyKeys(0, 1);
        assertThat(times(1)).containsExactly(25_000);
    }

    @Test
    void aDeletedFinishIsWithdrawnUntilTheNextFinish() {
        service.accept(finish(1, 1, 2, RefinedMessageType.Live, 25_000));
        service.accept(finish(1, 1, 3, RefinedMessageType.Live, 26_000));

        service.accept(deleted(1, 1, 2, 2, 25_000));

        assertThat(times(1)).isEmpty();
        assertThat(times(2)).containsExactly(26_000);

        service.accept(finish(1, 1, 2, RefinedMessageType.Live, 27_000));
        assertThat(times(1)).containsExactly(27_000);
    }

    @Test
    void aDeletedFinishIsWithdrawnWhateverItsLap() {
        service.accept(time(1, 1, 2, RefinedKindOfTime.Finish, RefinedMessageType.CurrentRaceResult, 1, RefinedTimeType.Normal, 25_000));

        service.accept(time(1, 1, 2, RefinedKindOfTime.Finish, RefinedMessageType.CurrentRaceResult, 1, RefinedTimeType.DeletedTime, 25_000));

        assertThat(service.getHeats()).isEmpty();
    }

    @Test
    void aPoolNumberedFromZeroStartsAtLaneIndex0() {
        service.accept(new UsedLanesMessage(LocalDateTime.of(2021, 6, 21, 14, 52), "WK", (short) 1, (byte) 1, "", List.of(0, 1), LaneNumbering.ZeroBased));
        service.accept(finish(1, 1, 0, RefinedMessageType.CurrentRaceResult, 25_000));
        service.accept(finish(1, 1, 1, RefinedMessageType.CurrentRaceResult, 26_000));

        assertThat(service.getHeats()[0].getLanes()).containsOnlyKeys(0, 1);
        assertThat(times(0)).containsExactly(25_000);
        assertThat(times(1)).containsExactly(26_000);
    }

    @Test
    void aRemovedHeatLosesItsTimes() {
        service.accept(finish(1, 1, 2, RefinedMessageType.CurrentRaceResult, 25_000));
        service.accept(finish(1, 2, 2, RefinedMessageType.CurrentRaceResult, 26_000));

        service.accept(new HeatRemovedMessage(LocalDateTime.of(2021, 6, 21, 14, 55), "WK", (short) 1, (byte) 1, ""));

        assertThat(service.getHeats()).extracting(Heat::getHeat).containsExactly(2);
        assertThat(times(1)).containsExactly(26_000);
    }

    @Test
    void aDeletedTimeOtherThanTheFinishKeepsTheFinish() {
        service.accept(finish(1, 1, 2, RefinedMessageType.Live, 25_000));

        service.accept(deleted(1, 1, 2, 1, 25_000));
        service.accept(deleted(1, 1, 2, 2, 24_000));
        service.accept(deleted(1, 2, 2, 2, 25_000));

        assertThat(service.getHeats()).hasSize(1);
        assertThat(times(1)).containsExactly(25_000);
    }

    @Test
    void onlyTheRoundThatBeganLastIsServedAlsoAfterACorrectionOfAnEarlierRound() {
        service.accept(finishOfRound("PreliminaryHeat", 1, 0, 25_000));
        service.accept(finishOfRound("PreliminaryHeat", 2, 0, 26_000));
        service.accept(finishOfRound("Final", 1, 1, 24_000));

        assertThat(service.getHeats()[0].getLanes()).containsOnlyKeys(0);
        assertThat(times(0)).containsExactly(24_000);

        service.accept(finishOfRound("PreliminaryHeat", 1, 2, 25_500));

        assertThat(service.getHeats()[0].getLanes()).containsOnlyKeys(0);
        assertThat(times(0)).containsExactly(24_000);
    }

    @Test
    void aRemovedHeatKeepsTheOtherRoundsOfItsNumber() {
        service.accept(finishOfRound("PreliminaryHeat", 1, 0, 25_000));
        service.accept(finishOfRound("Final", 1, 1, 24_000));

        service.accept(new HeatRemovedMessage(LocalDateTime.of(2021, 6, 21, 14, 55), "WK", (short) 1, (byte) 2, "Final"));

        assertThat(times(0)).containsExactly(25_000);
    }

    @Test
    void aDeletedFinishKeepsTheSameTimeOfAnotherRound() {
        service.accept(finishOfRound("PreliminaryHeat", 1, 0, 25_000));
        service.accept(finishOfRound("Final", 1, 1, 25_000));

        service.accept(new TimeMessage(LocalDateTime.of(2021, 6, 21, 14, 55), "WK", (short) 1, (byte) 2, "Final", RefinedMessageType.CurrentRaceResult,
                                       RefinedKindOfTime.Finish, (byte) 1, (byte) 2, (byte) 2, (byte) 0, 25_000, RefinedTimeType.DeletedTime,
                                       RefinedTimeOrigin.Primary));

        assertThat(times(0)).containsExactly(25_000);
    }

    @Test
    void ofRoundsWithTheSameArrivalTheLaterRoundOfTheCompetitionIsServed() {
        service.accept(finishOfRound("Final", 1, 0, 24_000));
        service.accept(finishOfRound("PreliminaryHeat", 1, 0, 25_000));

        assertThat(times(0)).containsExactly(24_000);
    }

    @Test
    void ofRoundsWithTheSameArrivalTheOneThatBeganLastIsServedWhenOneIsUnknown() {
        service.accept(finishOfRound("Final", 1, 0, 24_000));
        service.accept(finishOfRound("Extra", 1, 0, 25_000));

        assertThat(times(0)).containsExactly(25_000);

        service.accept(finishOfRound("Extra", 1, 1, 26_000));
        service.accept(finishOfRound("Final", 1, 1, 23_000));

        assertThat(times(0)).containsExactly(25_000, 26_000);
    }
}
