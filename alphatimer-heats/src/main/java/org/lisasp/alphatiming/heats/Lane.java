package org.lisasp.alphatiming.heats;

import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;

final class Lane {

    private final int number;
    private int timeInMillis;
    private int lap;
    private LaneStatus status;
    private Penalty penalty;
    private int place;
    private boolean finished;

    Lane(LaneDto dto) {
        this.number = dto.number();
        this.timeInMillis = dto.timeInMillis();
        this.lap = dto.lap();
        this.status = dto.status();
        this.penalty = dto.penalty();
        this.place = dto.place();
        this.finished = dto.finished();
        enforceStatus();
    }

    LaneDto createDto() {
        return new LaneDto(number, timeInMillis, lap, status, penalty, place, finished);
    }

    int number() {
        return number;
    }

    boolean isDone() {
        return status == LaneStatus.Used && (finished || penalty != Penalty.None);
    }

    boolean isUsed() {
        return status == LaneStatus.Used;
    }

    boolean ranks() {
        return status == LaneStatus.Used && finished && penalty == Penalty.None && timeInMillis > 0;
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
        penalty = Penalty.None;
    }

    void used(boolean used) {
        status = used ? LaneStatus.Used : LaneStatus.NotUsed;
        enforceStatus();
    }

    void touch(TimeMessage message) {
        if (message.getTimeInMillis() <= 0) {
            return;
        }
        timeInMillis = message.getTimeInMillis();
        lap = message.getCurrentLap();
        finished = message.getKindOfTime() == RefinedKindOfTime.Finish;
        enforceStatus();
    }

    void didNotStart() {
        penalty = Penalty.DidNotStart;
        timeInMillis = 0;
        lap = 0;
        place = 0;
        finished = false;
    }

    void didNotFinish() {
        penalty = Penalty.DidNotFinish;
        place = 0;
        finished = false;
    }

    private void enforceStatus() {
        if (status == LaneStatus.NotUsed) {
            timeInMillis = 0;
            lap = 0;
            place = 0;
            finished = false;
            penalty = Penalty.None;
        }
        if (timeInMillis <= 0) {
            timeInMillis = 0;
            lap = 0;
            finished = false;
        }
    }
}
