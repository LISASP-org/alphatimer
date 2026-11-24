package org.lisasp.alphatiming.lenex;

import org.lisasp.alphatiming.lenex.api.LenexMeet;
import org.lisasp.alphatiming.lenex.api.LenexEvent;
import org.lisasp.alphatiming.lenex.api.LenexHeat;
import org.lisasp.alphatiming.lenex.api.LenexResult;
import org.lisasp.alphatiming.lenex.api.LenexSplit;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DidNotFinishMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DidNotStartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DisqualifiedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.HeatRemovedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.OfficialEndMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.StartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.WithdrawnMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.LaneNumbering;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class LenexRefinerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 1, 10, 10, 0);
    private static final LenexMeet EMPTY = new LenexMeet(OptionalInt.empty(), List.of());

    private final LenexRefiner refiner = new LenexRefiner("WK");

    @Test
    void aNewResultBecomesItsUsedLanesSplitAndFinish() {
        List<RefinedMessage> messages = refiner.refine(EMPTY, meet(heat(2, "", result(4, 62340, "", split(50, 30120)))), AT);

        assertThat(messages).containsExactly(
                used(2, LaneNumbering.AssumedOneBased, 4),
                time(2, 4, RefinedKindOfTime.SplitTime, 1, 30120, RefinedTimeType.Normal),
                time(2, 4, RefinedKindOfTime.Finish, 2, 62340, RefinedTimeType.Normal));
    }

    @Test
    void theSameVersionAgainGivesNoMessages() {
        LenexMeet meet = meet(heat(2, "OFFICIAL", result(4, 62340, "", split(50, 30120))));

        assertThat(refiner.refine(meet, meet, AT)).isEmpty();
    }

    @Test
    void aChangedFinishIsDeletedAndSentAgain() {
        LenexMeet before = meet(heat(2, "", result(4, 62340, "")));
        LenexMeet after = meet(heat(2, "", result(4, 62350, "")));

        assertThat(refiner.refine(before, after, AT)).containsExactly(
                time(2, 4, RefinedKindOfTime.Finish, 2, 62340, RefinedTimeType.DeletedTime),
                time(2, 4, RefinedKindOfTime.Finish, 2, 62350, RefinedTimeType.Normal));
    }

    @Test
    void aSplitThatDisappearedIsDeleted() {
        LenexMeet before = meet(heat(2, "", result(4, 62340, "", split(50, 30120))));
        LenexMeet after = meet(heat(2, "", result(4, 62340, "")));

        assertThat(refiner.refine(before, after, AT)).containsExactly(
                time(2, 4, RefinedKindOfTime.SplitTime, 1, 30120, RefinedTimeType.DeletedTime));
    }

    @Test
    void theStatusOfAResultBecomesAPenalty() {
        List<RefinedMessage> messages = refiner.refine(EMPTY, meet(heat(2, "",
                result(1, 62340, "DSQ"), result(2, 0, "DNS"), result(3, 0, "DNF"), result(4, 0, "WDR"), result(5, 0, "SICK"),
                result(6, 63000, "EXH"), result(7, 0, "RJC"))), AT);

        assertThat(messages).containsExactly(
                used(2, LaneNumbering.AssumedOneBased, 1, 2, 3, 4, 5, 6),
                time(2, 1, RefinedKindOfTime.Finish, 2, 62340, RefinedTimeType.Normal),
                new DisqualifiedMessage(AT, "WK", (short) 3, (byte) 2, "TimedFinal", (byte) 1, false),
                new DidNotStartMessage(AT, "WK", (short) 3, (byte) 2, "TimedFinal", (byte) 2, false),
                new DidNotFinishMessage(AT, "WK", (short) 3, (byte) 2, "TimedFinal", (byte) 3, false),
                new WithdrawnMessage(AT, "WK", (short) 3, (byte) 2, "TimedFinal", (byte) 4, false),
                new WithdrawnMessage(AT, "WK", (short) 3, (byte) 2, "TimedFinal", (byte) 5, false),
                time(2, 6, RefinedKindOfTime.Finish, 2, 63000, RefinedTimeType.Normal));
    }

    @Test
    void aStatusThatDisappearedRemovesThePenalty() {
        LenexMeet before = meet(heat(2, "", result(1, 62340, "DSQ")));
        LenexMeet after = meet(heat(2, "", result(1, 62340, "")));

        assertThat(refiner.refine(before, after, AT)).containsExactly(
                new DisqualifiedMessage(AT, "WK", (short) 3, (byte) 2, "TimedFinal", (byte) 1, true));
    }

    @Test
    void aRunningHeatIsStartedOnce() {
        LenexMeet running = meet(heat(2, "RUNNING"));

        assertThat(refiner.refine(EMPTY, running, AT)).containsExactly(
                new StartMessage(AT, "WK", (short) 3, (byte) 2, "TimedFinal", RefinedMessageType.CurrentRaceResult, (byte) 2, (byte) 0, 0,
                                 RefinedTimeType.Normal),
                used(2, LaneNumbering.AssumedOneBased));
        assertThat(refiner.refine(running, running, AT)).isEmpty();
    }

    @Test
    void anOfficialHeatEndsOnce() {
        LenexMeet unofficial = meet(heat(2, "UNOFFICIAL", result(4, 62340, "")));
        LenexMeet official = meet(heat(2, "OFFICIAL", result(4, 62340, "")));

        assertThat(refiner.refine(unofficial, official, AT)).containsExactly(
                new OfficialEndMessage(AT, "WK", (short) 3, (byte) 2, "TimedFinal", (byte) 2));
        assertThat(refiner.refine(official, official, AT)).isEmpty();
    }

    @Test
    void aHeatThatDisappearedIsRemoved() {
        LenexMeet before = meet(heat(2, "", result(4, 62340, "")));

        assertThat(refiner.refine(before, meet(heat(2, "SEEDED")), AT)).containsExactly(
                new HeatRemovedMessage(AT, "WK", (short) 3, (byte) 2, "TimedFinal"));
    }

    @Test
    void heatsOfTheSameNumbersInDifferentRoundsStayApart() {
        LenexMeet meet = new LenexMeet(OptionalInt.empty(), List.of(
                new LenexEvent(3, "PRE", "LCM", 100, 1, List.of(heat(1, "", result(4, 62340, "")))),
                new LenexEvent(3, "FIN", "LCM", 100, 1, List.of(heat(1, "", result(4, 61000, ""))))));

        assertThat(refiner.refine(EMPTY, meet, AT)).extracting(RefinedMessage::getRound)
                                                    .containsExactly("PreliminaryHeat", "PreliminaryHeat", "Final", "Final");
    }

    @Test
    void theLaneNumberingFollowsThePoolOrLaneZero() {
        LenexMeet laneZero = meet(heat(2, "", result(0, 62340, "")));
        LenexMeet fromOne = new LenexMeet(OptionalInt.of(1), meet(heat(2, "", result(4, 62340, ""))).events());
        LenexMeet fromZero = new LenexMeet(OptionalInt.of(0), meet(heat(2, "", result(4, 62340, ""))).events());

        assertThat(refiner.refine(EMPTY, laneZero, AT).getFirst()).isEqualTo(used(2, LaneNumbering.ZeroBased, 0));
        assertThat(refiner.refine(EMPTY, fromOne, AT).getFirst()).isEqualTo(used(2, LaneNumbering.OneBased, 4));
        assertThat(refiner.refine(EMPTY, fromZero, AT).getFirst()).isEqualTo(used(2, LaneNumbering.ZeroBased, 4));
    }

    @Test
    void aLaneZeroOutweighsTheFirstLaneOfThePool() {
        LenexMeet meet = new LenexMeet(OptionalInt.of(1), meet(heat(2, "", result(0, 62340, ""))).events());

        assertThat(refiner.refine(EMPTY, meet, AT).getFirst()).isEqualTo(used(2, LaneNumbering.ZeroBased, 0));
    }

    private static LenexMeet meet(LenexHeat... heats) {
        return new LenexMeet(OptionalInt.empty(), List.of(new LenexEvent(3, "", "LCM", 100, 1, List.of(heats))));
    }

    private static LenexHeat heat(int number, String status, LenexResult... results) {
        return new LenexHeat(number, status, List.of(results));
    }

    private static LenexResult result(int lane, int millis, String status, LenexSplit... splits) {
        return new LenexResult(lane, millis > 0 ? OptionalInt.of(millis) : OptionalInt.empty(), status, List.of(splits));
    }

    private static LenexSplit split(int distance, int millis) {
        return new LenexSplit(distance, millis);
    }

    private static UsedLanesMessage used(int heat, LaneNumbering numbering, Integer... lanes) {
        return new UsedLanesMessage(AT, "WK", (short) 3, (byte) heat, "TimedFinal", List.of(lanes), numbering);
    }

    private static TimeMessage time(int heat, int lane, RefinedKindOfTime kind, int lap, int millis, RefinedTimeType type) {
        return new TimeMessage(AT, "WK", (short) 3, (byte) heat, "TimedFinal", RefinedMessageType.CurrentRaceResult, kind, (byte) lane,
                               (byte) lap, (byte) 2, (byte) 0, millis, type);
    }
}
