package org.lisasp.alphatiming.heats;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @param penalties unmodifiable, empty for none
 * @param place     0 = no placing (yet)
 * @param times     every time of the lane in order of arrival, deleted ones included
 */
public record LaneDto(int number, LaneStatus status, Set<Penalty> penalties, int place, List<LaneTime> times) {

    public LaneDto {
        penalties = Set.copyOf(penalties);
        times = List.copyOf(times);
    }

    public Optional<LaneTime> finish() {
        return newest(TimeKind.Finish);
    }

    /** The finish, else the newest split: a split arriving after the finish does not replace it. */
    public Optional<LaneTime> current() {
        return finish().or(() -> newest(TimeKind.Split));
    }

    public List<LaneTime> backups() {
        return times.stream().filter(t -> t.kind() == TimeKind.Backup).toList();
    }

    private Optional<LaneTime> newest(TimeKind kind) {
        return times.reversed().stream().filter(t -> t.kind() == kind && !t.deleted()).findFirst();
    }
}
