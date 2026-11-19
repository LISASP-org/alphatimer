package org.lisasp.alphatiming.heats;

import java.time.LocalDateTime;

public record LaneTime(int lap, TimeKind kind, int timeInMillis, TimeSource source, TimeType type, boolean deleted, LocalDateTime arrived) {

    LaneTime asDeleted() {
        return new LaneTime(lap, kind, timeInMillis, source, type, true, arrived);
    }
}
