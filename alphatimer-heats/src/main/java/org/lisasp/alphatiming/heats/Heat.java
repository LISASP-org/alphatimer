package org.lisasp.alphatiming.heats;

import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DidNotFinishMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DidNotStartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.OfficialEndMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.StartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

final class Heat {

    private final String competition;
    private final int event;
    private final int heat;
    private HeatStatus status;
    private LocalDateTime started;
    private LocalDateTime ended;
    private LocalDateTime lastMessage;
    private int lapCount;
    private final List<Lane> lanes = new ArrayList<>();

    Heat(HeatDto dto) {
        this.competition = dto.competition();
        this.event = dto.event();
        this.heat = dto.heat();
        this.status = dto.status();
        this.started = dto.started();
        this.ended = dto.ended();
        this.lastMessage = dto.lastMessage();
        this.lapCount = dto.lapCount();
        dto.lanes().forEach(l -> lanes.add(new Lane(l)));
    }

    HeatDto createDto() {
        return new HeatDto(competition, event, heat, status, started, ended, lapCount, lanes.stream().map(Lane::createDto).toList(), lastMessage);
    }

    /** A start signal always (re)starts the heat: the clock restarts and the lanes are reset (false start, repeated start). */
    void start(StartMessage message) {
        status = HeatStatus.Started;
        started = message.getTimestamp();
        lastMessage = message.getTimestamp();
        ended = null;
        lapCount = message.getLapCount();
        lanes.forEach(Lane::start);
    }

    void usedLanes(UsedLanesMessage message) {
        UsedLanes used = UsedLanes.fromValue(message.getUsedLanes());
        lastMessage = message.getTimestamp();
        lanes.forEach(l -> l.used(used.isUsed(l.number() - 1)));
    }

    void touch(TimeMessage message) {
        Lane lane = lane(message.getLane());
        assureStarted(message);
        lastMessage = message.getTimestamp();
        syncStart(message);
        lapCount = message.getLapCount();
        lane.touch(message);
        update(message);
    }

    /**
     * A live time carries the race time of the device, and the message its arrival on the local clock; together they tell
     * when the race started on the local clock. Repeats of results arrive long after the race and must not be used.
     */
    private void syncStart(TimeMessage message) {
        if (status == HeatStatus.Started && message.getMessageType() == RefinedMessageType.Live && message.getTimeInMillis() > 0) {
            started = message.getTimestamp().minus(Duration.ofMillis(message.getTimeInMillis()));
        }
    }

    void didNotStart(DidNotStartMessage message) {
        Lane lane = lane(message.getLane());
        assureStarted(message);
        lastMessage = message.getTimestamp();
        lane.didNotStart();
        update(message);
    }

    void didNotFinish(DidNotFinishMessage message) {
        Lane lane = lane(message.getLane());
        assureStarted(message);
        lastMessage = message.getTimestamp();
        lane.didNotFinish();
        update(message);
    }

    void finish(OfficialEndMessage message) {
        assureStarted(message);
        status = HeatStatus.Finished;
        lastMessage = message.getTimestamp();
        if (ended == null) {
            ended = message.getTimestamp();
        }
        lapCount = message.getLapCount();
        computePlaces();
    }

    /**
     * Places are always computed from the final times (ties share a place): the device's ranks are provisional while the
     * race runs and are not always consistent with the times. Also stops the clock once every used lane is done.
     */
    private void update(RefinedMessage message) {
        computePlaces();
        List<Lane> used = lanes.stream().filter(Lane::isUsed).toList();
        if (ended == null && !used.isEmpty() && used.stream().allMatch(Lane::isDone)) {
            ended = message.getTimestamp();
        }
    }

    private void computePlaces() {
        List<Lane> ranking = lanes.stream().filter(Lane::ranks).toList();
        for (Lane lane : lanes) {
            lane.place(lane.ranks() ? 1 + (int) ranking.stream().filter(o -> o.timeInMillis() < lane.timeInMillis()).count() : 0);
        }
    }

    private Lane lane(int number) {
        return lanes.stream()
                    .filter(l -> l.number() == number)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException(String.format("%s %d %d: Lane %d not found.", competition, event, heat, number)));
    }

    private void assureStarted(RefinedMessage message) {
        if (status == HeatStatus.Open) {
            status = HeatStatus.Started;
        }
        if (started == null) {
            started = message.getTimestamp();
        }
    }
}
