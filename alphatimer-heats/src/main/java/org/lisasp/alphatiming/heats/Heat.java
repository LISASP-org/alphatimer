package org.lisasp.alphatiming.heats;

import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.BackupTimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DidNotFinishMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DisqualifiedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DidNotStartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.OfficialEndMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.ReadyToStartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.StartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.WithdrawnMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class Heat {

    private final int event;
    private final String round;
    private final int heat;
    private HeatStatus status;
    private LocalDateTime readyToStart;
    private LocalDateTime started;
    private LocalDateTime ended;
    private LocalDateTime lastMessage;
    private Integer startTimeOfDayMillis;
    private int lapCount;
    private final List<Lane> lanes = new ArrayList<>();

    Heat(HeatDto dto) {
        this.event = dto.event();
        this.round = dto.round();
        this.heat = dto.heat();
        this.status = dto.status();
        this.readyToStart = dto.readyToStart();
        this.started = dto.started();
        this.ended = dto.ended();
        this.lastMessage = dto.lastMessage();
        this.startTimeOfDayMillis = dto.startTimeOfDayMillis();
        this.lapCount = dto.lapCount();
        dto.lanes().forEach(l -> lanes.add(new Lane(l)));
    }

    HeatDto createDto() {
        return new HeatDto(event, round, heat, status, lapCount, readyToStart, started, ended, lastMessage, startTimeOfDayMillis, lanes.stream().map(Lane::createDto).toList());
    }

    void readyToStart(ReadyToStartMessage message) {
        if (status != HeatStatus.Open || started != null) {
            return;
        }
        readyToStart = message.getTimestamp();
        lastMessage = message.getTimestamp();
        lapCount = message.getLapCount();
    }

    /**
     * A live start (re)starts the heat: the clock restarts and the lanes are reset (false start, repeated start). Repeats
     * of the results and corrected or deleted starts arrive after the race and must not reset a heat that has started.
     */
    void start(StartMessage message) {
        if (status != HeatStatus.Open && !isLiveStart(message)) {
            return;
        }
        status = HeatStatus.Started;
        started = message.getTimestamp();
        startTimeOfDayMillis = message.getDeviceTimeInMillis().isPresent() ? message.getDeviceTimeInMillis().getAsInt() : null;
        lastMessage = message.getTimestamp();
        ended = null;
        lapCount = message.getLapCount();
        lanes.forEach(Lane::start);
    }

    private static boolean isLiveStart(StartMessage message) {
        return message.getMessageType() == RefinedMessageType.Live
                && (message.getTimeType() == RefinedTimeType.Normal || message.getTimeType() == RefinedTimeType.ManualTime);
    }

    /** The source numbers the lanes; a lane the heat does not have yet (lane 0, more than ten lanes) is added. */
    void usedLanes(UsedLanesMessage message) {
        lastMessage = message.getTimestamp();
        for (int number : message.getLanes()) {
            if (lanes.stream().noneMatch(l -> l.number() == number)) {
                lanes.add(new Lane(new LaneDto(number, LaneStatus.NotUsed, Set.of(), 0, List.of())));
            }
        }
        lanes.sort(Comparator.comparingInt(Lane::number));
        lanes.forEach(l -> l.used(message.getLanes().contains(l.number())));
        computePlaces();
    }

    void touch(TimeMessage message) {
        Lane lane = lane(message.getLane());
        if (message.getTimeType() == RefinedTimeType.DeletedTime) {
            deleteTime(lane, message);
            return;
        }
        assureStarted(message);
        lastMessage = message.getTimestamp();
        syncStart(message);
        lapCount = message.getLapCount();
        lane.touch(message);
        update(message);
    }

    void backupTime(BackupTimeMessage message) {
        Lane lane = lane(message.getLane());
        if (status == HeatStatus.Open || !lane.backup(message)) {
            return;
        }
        lastMessage = message.getTimestamp();
    }

    /**
     * The device withdraws a time by sending it again as deleted, after the race time has moved on; a deleted final time
     * comes as a split time of the last lap. Every time of the lane with that lap and value is marked as deleted. Only a heat
     * with live times runs on; a heat known from results alone was over when they arrived.
     */
    private void deleteTime(Lane lane, TimeMessage message) {
        lastMessage = message.getTimestamp();
        if (lane.deleteTime(message)) {
            computePlaces();
            if (status == HeatStatus.Started && !lane.isDone() && hasLiveTimes()) {
                ended = null;
            }
        }
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
        penalty(message, message.getLane(), Penalty.DidNotStart, message.isRemoved());
    }

    void didNotFinish(DidNotFinishMessage message) {
        penalty(message, message.getLane(), Penalty.DidNotFinish, message.isRemoved());
    }

    void disqualified(DisqualifiedMessage message) {
        penalty(message, message.getLane(), Penalty.Disqualified, message.isRemoved());
    }

    void withdrawn(WithdrawnMessage message) {
        penalty(message, message.getLane(), Penalty.Withdrawn, message.isRemoved());
    }

    private void penalty(RefinedMessage message, int laneNumber, Penalty penalty, boolean removed) {
        Lane lane = lane(laneNumber);
        if (removed) {
            removePenalty(lane, penalty, message);
            return;
        }
        assureStarted(message);
        lastMessage = message.getTimestamp();
        lane.penalize(penalty);
        update(message);
    }

    /** Like a deleted time: a heat that is not officially over is no longer ended while the lane lacks a result. */
    private void removePenalty(Lane lane, Penalty penalty, RefinedMessage message) {
        lastMessage = message.getTimestamp();
        if (lane.removePenalty(penalty)) {
            computePlaces();
            if (status == HeatStatus.Started && !lane.isDone() && hasLiveTimes()) {
                ended = null;
            }
        }
    }

    /**
     * Results of a heat without live times (a LENEX file) arrive after the race; the arrival of the version that started
     * and ended it is its end, and its start lies the longest finish before, unless that is before the end of the heat that
     * ended last. Later corrections arrive after the end and do not move the heat.
     */
    void timeFromResults(LocalDateTime arrival, LocalDateTime lastEndOfOtherHeats) {
        if (started == null || ended == null || !started.equals(arrival) || !ended.equals(arrival) || hasLiveTimes()) {
            return;
        }
        int longest = lanes.stream().filter(Lane::isUsed).mapToInt(Lane::finishInMillis).max().orElse(0);
        LocalDateTime start = arrival.minus(Duration.ofMillis(longest));
        if (longest > 0 && (lastEndOfOtherHeats == null || !start.isBefore(lastEndOfOtherHeats))) {
            started = start;
        }
    }

    private boolean hasLiveTimes() {
        return lanes.stream().anyMatch(Lane::hasLiveTime);
    }

    void estimateEnd(LocalDateTime at) {
        status = HeatStatus.FinishedEstimated;
        ended = at;
    }

    void finish(OfficialEndMessage message) {
        assureStarted(message);
        if (status == HeatStatus.FinishedEstimated) {
            ended = null;
        }
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
            lane.place(lane.ranks() ? 1 + (int) ranking.stream().filter(o -> o.finishInMillis() < lane.finishInMillis()).count() : 0);
        }
    }

    private Lane lane(int number) {
        return lanes.stream()
                    .filter(l -> l.number() == number)
                    .findFirst()
                    .orElseThrow(() -> new UnknownLaneException(event, heat, number));
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
