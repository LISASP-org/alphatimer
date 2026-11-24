package org.lisasp.alphatiming.legacy.server.refined;

import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessageListener;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
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

    private final LaneTimeRepository store;

    public RefinedLegacyService(LaneTimeRepository store) {
        this.store = store;
    }

    @Override
    public void accept(RefinedMessage message) {
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
                                time.getLane() - 1,
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

    /** The device withdraws a finish by sending it again as a deleted split time of the last lap. */
    private void deleteFinish(TimeMessage time) {
        if (time.getCurrentLap() != time.getLapCount()) {
            return;
        }
        store.delete(time.getEvent(), time.getHeat(), time.getLane() - 1, time.getTimeInMillis());
    }

    public Heat[] getHeats() {
        HashMap<String, Heat> heats = new HashMap<>();
        List<LaneTime> laneTimes = store.findAll().stream().sorted().toList();
        for (LaneTime laneTime : laneTimes) {
            Heat heat = heats.computeIfAbsent(Heat.computeKey(laneTime.event(), laneTime.heatNumber()),
                                              key -> new Heat("" + (heats.size() + 1), laneTime.event(), laneTime.heatNumber()));
            heat.store(laneTime.laneIndex(), laneTime.timeInMillis(), laneTime.status());
        }
        return heats.values().stream().sorted().toArray(Heat[]::new);
    }
}
