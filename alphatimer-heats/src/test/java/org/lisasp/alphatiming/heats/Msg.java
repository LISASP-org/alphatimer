package org.lisasp.alphatiming.heats;

import org.lisasp.alphatimer.api.refinedmessages.accepted.DidNotFinishMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DidNotStartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.OfficialEndMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.StartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;

import java.time.LocalDateTime;

/** Test helper that builds refined messages for competition "WK". */
public final class Msg {

    public static final LocalDateTime T0 = LocalDateTime.of(2026, 5, 1, 10, 0, 0);

    private Msg() {
    }

    public static UsedLanesMessage used(int event, int heat, String lanes) {
        return new UsedLanesMessage(T0, "WK", (short) event, (byte) heat, lanes);
    }

    public static StartMessage start(int event, int heat, LocalDateTime at) {
        return new StartMessage(at, "WK", (short) event, (byte) heat, RefinedMessageType.Live, (byte) 2, (byte) 0, 0, RefinedTimeType.Normal);
    }

    /** The device repeating the results of an earlier race. */
    public static StartMessage startPrevious(int event, int heat, LocalDateTime at) {
        return new StartMessage(at, "WK", (short) event, (byte) heat, RefinedMessageType.PreviousRaceResult, (byte) 2, (byte) 0, 0, RefinedTimeType.Normal);
    }

    public static TimeMessage splitWithRank(int event, int heat, int lane, int lap, int rank, int millis) {
        return time(event, heat, lane, lap, rank, millis, RefinedKindOfTime.SplitTime);
    }

    public static TimeMessage split(int event, int heat, int lane, int lap, int millis) {
        return time(event, heat, lane, lap, 0, millis, RefinedKindOfTime.SplitTime);
    }

    public static TimeMessage fin(int event, int heat, int lane, int rank, int millis) {
        return time(event, heat, lane, 2, rank, millis, RefinedKindOfTime.Finish);
    }

    public static TimeMessage splitAt(int event, int heat, int lane, int lap, int millis, LocalDateTime at) {
        return timeAt(event, heat, lane, lap, 0, millis, RefinedKindOfTime.SplitTime, at, RefinedMessageType.Live);
    }

    public static TimeMessage finAt(int event, int heat, int lane, int millis, LocalDateTime at) {
        return timeAt(event, heat, lane, 2, 0, millis, RefinedKindOfTime.Finish, at, RefinedMessageType.Live);
    }

    /** The device repeating the result of the race that just ended. */
    public static TimeMessage resultAt(int event, int heat, int lane, int millis, LocalDateTime at) {
        return timeAt(event, heat, lane, 2, 1, millis, RefinedKindOfTime.Finish, at, RefinedMessageType.CurrentRaceResult);
    }

    private static TimeMessage timeAt(int event, int heat, int lane, int lap, int rank, int millis, RefinedKindOfTime kind, LocalDateTime at, RefinedMessageType type) {
        return new TimeMessage(at, "WK", (short) event, (byte) heat, type, kind, (byte) lane, (byte) lap, (byte) 2, (byte) rank, millis, RefinedTimeType.Normal);
    }

    private static TimeMessage time(int event, int heat, int lane, int lap, int rank, int millis, RefinedKindOfTime kind) {
        return new TimeMessage(T0.plusSeconds(1), "WK", (short) event, (byte) heat, RefinedMessageType.Live, kind, (byte) lane, (byte) lap, (byte) 2, (byte) rank, millis, RefinedTimeType.Normal);
    }

    public static DidNotStartMessage dns(int event, int heat, int lane) {
        return new DidNotStartMessage(T0, "WK", (short) event, (byte) heat, (byte) lane);
    }

    public static DidNotFinishMessage dnf(int event, int heat, int lane) {
        return new DidNotFinishMessage(T0, "WK", (short) event, (byte) heat, (byte) lane);
    }

    public static OfficialEndMessage end(int event, int heat, LocalDateTime at) {
        return new OfficialEndMessage(at, "WK", (short) event, (byte) heat, (byte) 2);
    }
}
