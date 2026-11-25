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
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.Round;
import org.lisasp.alphatiming.legacy.api.Heat;
import org.lisasp.alphatiming.legacy.api.LaneStatus;
import org.lisasp.alphatiming.legacy.server.LaneTime;
import org.lisasp.alphatiming.legacy.server.LaneTimeRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The heats of {@code LegacyService}, built from the refined messages instead of the ARES messages. */
public final class RefinedLegacyService implements RefinedMessageListener {

    /**
     * Holds the lane numbers as the source sent them in {@code LaneTime.laneIndex}; {@link #getHeats()} turns them into the
     * legacy index with the newest numbering, so times stored before a source renumbered its pool still line up.
     */
    private final LaneTimeRepository store;
    private LaneNumbering numbering = LaneNumbering.AssumedOneBased;
    /** The arrival of the first time of each round of an event and heat number, in the order the rounds began. */
    private final Map<HeatRound, LocalDateTime> begun = new LinkedHashMap<>();

    private record HeatRound(int event, int heat, String round) {
    }

    public RefinedLegacyService(LaneTimeRepository store) {
        this.store = store;
    }

    @Override
    public synchronized void accept(RefinedMessage message) {
        if (message instanceof UsedLanesMessage used) {
            numbering = used.getNumbering();
            return;
        }
        if (message instanceof HeatRemovedMessage removed) {
            removeHeat(new HeatRound(removed.getEvent(), removed.getHeat(), removed.getRound()));
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
        begun.putIfAbsent(new HeatRound(time.getEvent(), time.getHeat(), time.getRound()), time.getTimestamp());
        store.save(new LaneTime(time.getCompetition(),
                                time.getEvent(),
                                time.getHeat(),
                                time.getRound(),
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
        store.delete(time.getEvent(), time.getHeat(), time.getRound(), time.getLane(), time.getTimeInMillis());
    }

    private void removeHeat(HeatRound removed) {
        begun.remove(removed);
        store.findAll().stream()
             .filter(t -> removed.equals(roundOf(t)))
             .forEach(t -> store.delete(t.event(), t.heatNumber(), t.round(), t.laneIndex(), t.timeInMillis()));
    }

    private static HeatRound roundOf(LaneTime laneTime) {
        return new HeatRound(laneTime.event(), laneTime.heatNumber(), laneTime.round());
    }

    public synchronized Heat[] getHeats() {
        int firstLane = numbering == LaneNumbering.ZeroBased ? 0 : 1;
        HashMap<String, Heat> heats = new HashMap<>();
        List<LaneTime> stored = store.findAll();
        Map<String, String> servedRounds = servedRounds(stored);
        List<LaneTime> laneTimes = stored.stream().sorted().toList();
        for (LaneTime laneTime : laneTimes) {
            if (!laneTime.round().equals(servedRounds.get(keyOf(laneTime)))) {
                continue;
            }
            Heat heat = heats.computeIfAbsent(Heat.computeKey(laneTime.event(), laneTime.heatNumber()),
                                              key -> new Heat("" + (heats.size() + 1), laneTime.event(), laneTime.heatNumber()));
            heat.store(laneTime.laneIndex() - firstLane, laneTime.timeInMillis(), laneTime.status());
        }
        return heats.values().stream().sorted().toArray(Heat[]::new);
    }

    private static String keyOf(LaneTime laneTime) {
        return Heat.computeKey(laneTime.event(), laneTime.heatNumber());
    }

    /**
     * The legacy format has no round, so of the rounds sharing an event and heat number only the one that began last is
     * served; a correction of an earlier round does not bring that round back. All times of one LENEX version share its
     * arrival; such a tie goes to the later round of the competition, or to the round whose first time was sent last when
     * one of them is not a known {@link Round}.
     */
    private Map<String, String> servedRounds(List<LaneTime> stored) {
        Set<HeatRound> withTimes = stored.stream().map(RefinedLegacyService::roundOf).collect(Collectors.toSet());
        Map<String, List<HeatRound>> byHeat = begun.keySet().stream()
                                                   .filter(withTimes::contains)
                                                   .collect(Collectors.groupingBy(r -> Heat.computeKey(r.event(), r.heat())));
        Map<String, String> rounds = new HashMap<>();
        byHeat.forEach((key, candidates) -> rounds.put(key, newestRound(candidates)));
        return rounds;
    }

    private String newestRound(List<HeatRound> candidates) {
        LocalDateTime newest = candidates.stream().map(begun::get).max(LocalDateTime::compareTo).orElseThrow();
        List<String> tied = candidates.stream().filter(r -> begun.get(r).equals(newest)).map(HeatRound::round).toList();
        if (!tied.stream().allMatch(RefinedLegacyService::isKnownRound)) {
            return tied.getLast();
        }
        return tied.stream().max(Comparator.comparing(Round::valueOf)).orElseThrow();
    }

    private static boolean isKnownRound(String round) {
        return Arrays.stream(Round.values()).anyMatch(r -> r.name().equals(round));
    }
}
