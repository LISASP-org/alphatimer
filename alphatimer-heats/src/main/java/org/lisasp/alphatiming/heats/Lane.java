package org.lisasp.alphatiming.heats;

import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;

import java.util.EnumSet;
import java.util.Set;

final class Lane {

    private final int number;
    private int timeInMillis;
    private int lap;
    private LaneStatus status;
    private final Set<Penalty> penalties = EnumSet.noneOf(Penalty.class);
    private int place;
    private boolean finished;

    Lane(LaneDto dto) {
        this.number = dto.number();
        this.timeInMillis = dto.timeInMillis();
        this.lap = dto.lap();
        this.status = dto.status();
        this.penalties.addAll(dto.penalties());
        this.place = dto.place();
        this.finished = dto.finished();
        enforceStatus();
    }

    LaneDto createDto() {
        return new LaneDto(number, timeInMillis, lap, status, penalties, place, finished);
    }

    int number() {
        return number;
    }

    boolean isDone() {
        return status == LaneStatus.Used && (finished || !penalties.isEmpty());
    }

    boolean isUsed() {
        return status == LaneStatus.Used;
    }

    boolean ranks() {
        return status == LaneStatus.Used && finished && penalties.isEmpty() && timeInMillis > 0;
    }

    int timeInMillis() {
        return timeInMillis;
    }

    int place() {
        return place;
    }

    void place(int place) {
        this.place = place;
    }

    void start() {
        timeInMillis = 0;
        lap = 0;
        place = 0;
        finished = false;
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
        if (finished && message.getKindOfTime() != RefinedKindOfTime.Finish) {
            return;
        }
        timeInMillis = message.getTimeInMillis();
        lap = message.getCurrentLap();
        finished = message.getKindOfTime() == RefinedKindOfTime.Finish;
        enforceStatus();
    }

    boolean deleteTime(TimeMessage message) {
        if (timeInMillis <= 0 || message.getTimeInMillis() != timeInMillis || message.getCurrentLap() != lap) {
            return false;
        }
        timeInMillis = 0;
        enforceStatus();
        return true;
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
            timeInMillis = 0;
            lap = 0;
            place = 0;
            finished = false;
            penalties.clear();
        }
        if (timeInMillis <= 0) {
            timeInMillis = 0;
            lap = 0;
            finished = false;
        }
    }
}
