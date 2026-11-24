package org.lisasp.alphatiming.legacy.server.refined;

import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessageListener;
import org.lisasp.alphatimer.api.refinedmessages.accepted.HeatRemovedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.LaneNumbering;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeOrigin;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;
import org.lisasp.alphatiming.legacy.api.Heat;
import org.lisasp.alphatiming.legacy.api.LaneStatus;
import org.lisasp.alphatiming.legacy.server.LaneTime;
import org.lisasp.alphatiming.legacy.server.LaneTimeRepository;

import java.util.HashMap;
import java.util.List;

/** The heats of {@code LegacyService}, built from the refined messages instead of the ARES messages. */
public final class RefinedLegacyService implements RefinedMessageListener {

    /**
     * Holds the lane numbers as the source sent them in {@code LaneTime.laneIndex}; {@link #getHeats()} turns them into the
     * legacy index with the newest numbering, so times stored before a source renumbered its pool still line up.
     */
    private final LaneTimeRepository store;
    private volatile LaneNumbering numbering = LaneNumbering.AssumedOneBased;

    public RefinedLegacyService(LaneTimeRepository store) {
        this.store = store;
    }

    @Override
    public void accept(RefinedMessage message) {
        if (message instanceof UsedLanesMessage used) {
            numbering = used.getNumbering();
            return;
        }
        if (message instanceof HeatRemovedMessage removed) {
            removeHeat(removed.getEvent(), removed.getHeat());
            return;
        }
        if (!(message instanceof TimeMessage time) || time.getOrigin() == RefinedTimeOrigin.Backup) {
            return;
        }
        if (time.getEvent() <= 0 || time.getHeat() <= 0) {
            return;
        }
        if (time.getTimeType() == RefinedTimeType.DeletedTime) {
            deleteFinish(time);
            return;
        }
        if (time.getKindOfTime() != RefinedKindOfTime.Finish) {
            return;
        }
        store.save(new LaneTime(time.getCompetition(),
                                time.getEvent(),
                                time.getHeat(),
                                time.getLane(),
                                time.getTimeInMillis(),
                                statusOf(time),
                                time.getTimestamp()));
    }

    private static LaneStatus statusOf(TimeMessage time) {
        return switch (time.getMessageType()) {
            case Live -> LaneStatus.RaceTimes;
            case CurrentRaceResult -> LaneStatus.ResultsOfTheRace;
            case PreviousRaceResult -> LaneStatus.ResultsOfThePreviousRace;
        };
    }

    /** ARES withdraws a finish as a deleted split time of the last lap, LENEX as a deleted finish. */
    private void deleteFinish(TimeMessage time) {
        if (time.getKindOfTime() != RefinedKindOfTime.Finish && time.getCurrentLap() != time.getLapCount()) {
            return;
        }
        store.delete(time.getEvent(), time.getHeat(), time.getLane(), time.getTimeInMillis());
    }

    private void removeHeat(int event, int heat) {
        store.findAll().stream()
             .filter(t -> t.event() == event && t.heatNumber() == heat)
             .forEach(t -> store.delete(t.event(), t.heatNumber(), t.laneIndex(), t.timeInMillis()));
    }

    public Heat[] getHeats() {
        int firstLane = numbering == LaneNumbering.ZeroBased ? 0 : 1;
        HashMap<String, Heat> heats = new HashMap<>();
        List<LaneTime> laneTimes = store.findAll().stream().sorted().toList();
        for (LaneTime laneTime : laneTimes) {
            Heat heat = heats.computeIfAbsent(Heat.computeKey(laneTime.event(), laneTime.heatNumber()),
                                              key -> new Heat("" + (heats.size() + 1), laneTime.event(), laneTime.heatNumber()));
            heat.store(laneTime.laneIndex() - firstLane, laneTime.timeInMillis(), laneTime.status());
        }
        return heats.values().stream().sorted().toArray(Heat[]::new);
    }
}
