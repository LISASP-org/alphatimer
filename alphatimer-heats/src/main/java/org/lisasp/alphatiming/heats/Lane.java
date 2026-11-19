package org.lisasp.alphatiming.heats;

import org.lisasp.alphatimer.api.refinedmessages.accepted.BackupTimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class Lane {

    private final int number;
    private LaneStatus status;
    private final Set<Penalty> penalties = EnumSet.noneOf(Penalty.class);
    private int place;
    private final List<LaneTime> times = new ArrayList<>();

    Lane(LaneDto dto) {
        this.number = dto.number();
        this.status = dto.status();
        this.penalties.addAll(dto.penalties());
        this.place = dto.place();
        this.times.addAll(dto.times());
        enforceStatus();
    }

    LaneDto createDto() {
        return new LaneDto(number, status, penalties, place, times);
    }

    int number() {
        return number;
    }

    boolean isDone() {
        return status == LaneStatus.Used && (finish().isPresent() || !penalties.isEmpty());
    }

    boolean isUsed() {
        return status == LaneStatus.Used;
    }

    boolean ranks() {
        return status == LaneStatus.Used && penalties.isEmpty() && finishInMillis() > 0;
    }

    int finishInMillis() {
        return finish().map(LaneTime::timeInMillis).orElse(0);
    }

    private Optional<LaneTime> finish() {
        return createDto().finish();
    }

    int place() {
        return place;
    }

    void place(int place) {
        this.place = place;
    }

    void start() {
        times.clear();
        place = 0;
        penalties.clear();
    }

    void used(boolean used) {
        status = used ? LaneStatus.Used : LaneStatus.NotUsed;
        enforceStatus();
    }

    void touch(TimeMessage message) {
        if (message.getTimeInMillis() <= 0) {
            return;
        }
        TimeKind kind = message.getKindOfTime() == RefinedKindOfTime.Finish ? TimeKind.Finish : TimeKind.Split;
        int lap = message.getCurrentLap();
        if (times.stream().noneMatch(t -> t.sameTimeAs(lap, kind, message.getTimeInMillis()))) {
            times.add(new LaneTime(lap, kind, message.getTimeInMillis(), sourceOf(message), typeOf(message), false, message.getTimestamp()));
        }
        enforceStatus();
    }

    void backup(BackupTimeMessage message) {
        if (message.getTimeInMillis() <= 0) {
            return;
        }
        int lap = message.getCurrentLap();
        if (times.stream().noneMatch(t -> t.sameTimeAs(lap, TimeKind.Backup, message.getTimeInMillis()))) {
            TimeSource source = message.getMessageType() == RefinedMessageType.Live ? TimeSource.Live : TimeSource.Result;
            times.add(new LaneTime(lap, TimeKind.Backup, message.getTimeInMillis(), source, TimeType.Normal, false, message.getTimestamp()));
        }
        enforceStatus();
    }

    boolean deleteTime(TimeMessage message) {
        boolean changed = false;
        for (int i = 0; i < times.size(); i++) {
            LaneTime time = times.get(i);
            if (!time.deleted() && time.kind() != TimeKind.Backup && time.lap() == message.getCurrentLap() && time.timeInMillis() == message.getTimeInMillis()) {
                times.set(i, time.asDeleted());
                changed = true;
            }
        }
        return changed;
    }

    private static TimeSource sourceOf(TimeMessage message) {
        return message.getMessageType() == RefinedMessageType.Live ? TimeSource.Live : TimeSource.Result;
    }

    private static TimeType typeOf(TimeMessage message) {
        return switch (message.getTimeType()) {
            case ManualTime -> TimeType.Manual;
            case CorrectedTime -> TimeType.Corrected;
            default -> TimeType.Normal;
        };
    }

    void didNotStart() {
        penalize(Penalty.DidNotStart);
    }

    void didNotFinish() {
        penalize(Penalty.DidNotFinish);
    }

    void disqualified() {
        penalize(Penalty.Disqualified);
    }

    private void penalize(Penalty penalty) {
        penalties.add(penalty);
        place = 0;
    }

    private void enforceStatus() {
        if (status == LaneStatus.NotUsed) {
            times.clear();
            place = 0;
            penalties.clear();
        }
    }
}
