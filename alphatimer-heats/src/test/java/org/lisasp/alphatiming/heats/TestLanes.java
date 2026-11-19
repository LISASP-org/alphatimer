package org.lisasp.alphatiming.heats;

import java.util.Set;

import static org.lisasp.alphatiming.heats.TestMessageCreator.T0;

public final class TestLanes {

    private TestLanes() {
    }

    public static LaneTime split(int lap, int timeInMillis) {
        return new LaneTime(lap, TimeKind.Split, timeInMillis, TimeSource.Live, TimeType.Normal, false, T0.plusSeconds(1));
    }

    public static LaneTime finish(int lap, int timeInMillis) {
        return new LaneTime(lap, TimeKind.Finish, timeInMillis, TimeSource.Live, TimeType.Normal, false, T0.plusSeconds(1));
    }

    public static LaneDto lane(int number, LaneStatus status, Set<Penalty> penalties, int place, LaneTime... times) {
        return new LaneDto(number, status, penalties, place, java.util.List.of(times));
    }
}
