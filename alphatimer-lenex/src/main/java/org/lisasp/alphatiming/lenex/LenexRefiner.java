package org.lisasp.alphatiming.lenex;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DidNotFinishMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DidNotStartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.DisqualifiedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.HeatRemovedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.OfficialEndMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.StartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.WithdrawnMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.LaneNumbering;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Turns two versions of a LENEX file into the refined messages that lead from the first to the second. Each version is the
 * complete state of the meet: what the newer version lacks was removed. A heat counts once it has a result or is running.
 */
@Slf4j
public final class LenexRefiner {

    private enum Penalty {
        DidNotStart, DidNotFinish, Disqualified, Withdrawn
    }

    private record HeatKey(int event, String round, int heat) {
    }

    private record TimeKey(RefinedKindOfTime kind, int lap) {
    }

    private record Lane(Map<TimeKey, Integer> times, Optional<Penalty> penalty) {
    }

    private record Heat(HeatKey key, String status, int lapCount, SortedMap<Integer, Lane> lanes) {
    }

    private record Version(LaneNumbering numbering, Map<HeatKey, Heat> heats) {
    }

    private final String competition;

    public LenexRefiner(String competition) {
        this.competition = competition;
    }

    public List<RefinedMessage> refine(LenexMeet previous, LenexMeet next, LocalDateTime arrival) {
        Version before = version(previous);
        Version after = version(next);
        List<RefinedMessage> messages = new ArrayList<>();
        for (Heat heat : after.heats().values()) {
            Heat old = before.heats().get(heat.key());
            refineHeat(old, heat, before.numbering() != after.numbering(), after.numbering(), arrival, messages);
        }
        for (HeatKey key : before.heats().keySet()) {
            if (!after.heats().containsKey(key)) {
                messages.add(new HeatRemovedMessage(arrival, competition, (short) key.event(), (byte) key.heat(), key.round()));
            }
        }
        return messages;
    }

    private void refineHeat(Heat old, Heat heat, boolean renumbered, LaneNumbering numbering, LocalDateTime at, List<RefinedMessage> messages) {
        HeatKey key = heat.key();
        short event = (short) key.event();
        byte number = (byte) key.heat();
        if ("RUNNING".equals(heat.status()) && (old == null || !"RUNNING".equals(old.status()))) {
            messages.add(new StartMessage(at, competition, event, number, key.round(), RefinedMessageType.CurrentRaceResult, (byte) heat.lapCount(),
                                          (byte) 0, 0, RefinedTimeType.Normal));
        }
        if (old == null || renumbered || !old.lanes().keySet().equals(heat.lanes().keySet())) {
            messages.add(new UsedLanesMessage(at, competition, event, number, key.round(), heat.lanes().keySet(), numbering));
        }
        for (Map.Entry<Integer, Lane> entry : heat.lanes().entrySet()) {
            Lane before = old == null ? null : old.lanes().get(entry.getKey());
            refineLane(key, heat.lapCount(), entry.getKey(), before, entry.getValue(), at, messages);
        }
        if ("OFFICIAL".equals(heat.status()) && (old == null || !"OFFICIAL".equals(old.status()))) {
            messages.add(new OfficialEndMessage(at, competition, event, number, key.round(), (byte) heat.lapCount()));
        }
    }

    private void refineLane(HeatKey key, int lapCount, int lane, Lane before, Lane after, LocalDateTime at, List<RefinedMessage> messages) {
        Map<TimeKey, Integer> oldTimes = before == null ? Map.of() : before.times();
        oldTimes.forEach((time, millis) -> {
            if (!millis.equals(after.times().get(time))) {
                messages.add(time(key, lapCount, lane, time, millis, RefinedTimeType.DeletedTime, at));
            }
        });
        after.times().forEach((time, millis) -> {
            if (!millis.equals(oldTimes.get(time))) {
                messages.add(time(key, lapCount, lane, time, millis, RefinedTimeType.Normal, at));
            }
        });
        Optional<Penalty> oldPenalty = before == null ? Optional.empty() : before.penalty();
        if (!oldPenalty.equals(after.penalty())) {
            oldPenalty.ifPresent(p -> messages.add(penalty(key, lane, p, true, at)));
            after.penalty().ifPresent(p -> messages.add(penalty(key, lane, p, false, at)));
        }
    }

    private TimeMessage time(HeatKey key, int lapCount, int lane, TimeKey time, int millis, RefinedTimeType type, LocalDateTime at) {
        return new TimeMessage(at, competition, (short) key.event(), (byte) key.heat(), key.round(), RefinedMessageType.CurrentRaceResult, time.kind(),
                               (byte) lane, (byte) time.lap(), (byte) lapCount, (byte) 0, millis, type);
    }

    private RefinedMessage penalty(HeatKey key, int lane, Penalty penalty, boolean removed, LocalDateTime at) {
        short event = (short) key.event();
        byte heat = (byte) key.heat();
        return switch (penalty) {
            case DidNotStart -> new DidNotStartMessage(at, competition, event, heat, key.round(), (byte) lane, removed);
            case DidNotFinish -> new DidNotFinishMessage(at, competition, event, heat, key.round(), (byte) lane, removed);
            case Disqualified -> new DisqualifiedMessage(at, competition, event, heat, key.round(), (byte) lane, removed);
            case Withdrawn -> new WithdrawnMessage(at, competition, event, heat, key.round(), (byte) lane, removed);
        };
    }

    private static Version version(LenexMeet meet) {
        Map<HeatKey, Heat> heats = new LinkedHashMap<>();
        boolean laneZero = false;
        for (LenexEvent event : meet.events()) {
            String round = LenexRounds.consolidated(event.round());
            if (event.number() < 1 || event.number() > Short.MAX_VALUE) {
                log.warn("Skipped event {}: its number does not fit", event.number());
                continue;
            }
            for (LenexHeat lenexHeat : event.heats()) {
                Optional<Heat> heat = heat(event, round, lenexHeat);
                if (heat.isEmpty()) {
                    continue;
                }
                if (heats.putIfAbsent(heat.get().key(), heat.get()) != null) {
                    log.warn("Skipped heat {}/{}/{}: the file holds it twice", event.number(), round, lenexHeat.number());
                    continue;
                }
                laneZero |= heat.get().lanes().containsKey(0);
            }
        }
        return new Version(numbering(meet, laneZero), heats);
    }

    private static Optional<Heat> heat(LenexEvent event, String round, LenexHeat heat) {
        if (heat.number() < 1 || heat.number() > Byte.MAX_VALUE) {
            log.warn("Skipped heat {} of event {}: its number does not fit", heat.number(), event.number());
            return Optional.empty();
        }
        Laps laps = Laps.of(event);
        SortedMap<Integer, Lane> lanes = new TreeMap<>();
        for (LenexResult result : heat.results()) {
            Optional<Optional<Penalty>> penalty = penalty(result.status());
            if (penalty.isEmpty()) {
                log.debug("Skipped the result in lane {} of heat {}/{} with status {}", result.lane(), event.number(), heat.number(), result.status());
                continue;
            }
            if (result.lane() < 0 || result.lane() > Byte.MAX_VALUE) {
                log.warn("Skipped lane {} of heat {}/{}: its number does not fit", result.lane(), event.number(), heat.number());
                continue;
            }
            lanes.put(result.lane(), new Lane(times(result, laps), penalty.get()));
        }
        if (lanes.isEmpty() && !"RUNNING".equals(heat.status())) {
            return Optional.empty();
        }
        return Optional.of(new Heat(new HeatKey(event.number(), round, heat.number()), heat.status(), laps.lapCount(heat), lanes));
    }

    /** The lap count of an event: from the pool length, or without a known pool one lap per split and one for the finish. */
    private record Laps(LenexEvent event, OptionalInt laps) {

        static Laps of(LenexEvent event) {
            return new Laps(event, Courses.laps(event.course(), event.distance() * event.relayCount()));
        }

        int lapCount(LenexHeat heat) {
            if (laps.isPresent()) {
                return laps.getAsInt();
            }
            return 1 + heat.results().stream().mapToInt(r -> r.splits().size()).max().orElse(0);
        }

        int lapOf(LenexSplit split, int index) {
            return Courses.laps(event.course(), split.distance()).orElse(index + 1);
        }
    }

    private static Map<TimeKey, Integer> times(LenexResult result, Laps laps) {
        Map<TimeKey, Integer> times = new LinkedHashMap<>();
        List<LenexSplit> splits = result.splits().stream().sorted(Comparator.comparingInt(LenexSplit::distance)).toList();
        for (int i = 0; i < splits.size(); i++) {
            times.put(new TimeKey(RefinedKindOfTime.SplitTime, laps.lapOf(splits.get(i), i)), splits.get(i).swimTimeInMillis());
        }
        int finishLap = laps.laps().orElse(splits.size() + 1);
        result.swimTimeInMillis().ifPresent(millis -> times.put(new TimeKey(RefinedKindOfTime.Finish, finishLap), millis));
        return times;
    }

    /** Empty for a status whose result is skipped ({@code RJC}, unknown values); else the penalty, if the status is one. */
    private static Optional<Optional<Penalty>> penalty(String status) {
        return switch (status) {
            case "", "EXH" -> Optional.of(Optional.empty());
            case "DSQ" -> Optional.of(Optional.of(Penalty.Disqualified));
            case "DNS" -> Optional.of(Optional.of(Penalty.DidNotStart));
            case "DNF" -> Optional.of(Optional.of(Penalty.DidNotFinish));
            case "WDR", "SICK" -> Optional.of(Optional.of(Penalty.Withdrawn));
            default -> Optional.empty();
        };
    }

    /** A lane 0 that occurs outweighs {@code lanemin}: files name the pool of the meet, while sessions may use another. */
    private static LaneNumbering numbering(LenexMeet meet, boolean laneZero) {
        if (laneZero) {
            return LaneNumbering.ZeroBased;
        }
        if (meet.laneMin().isPresent()) {
            return meet.laneMin().getAsInt() == 0 ? LaneNumbering.ZeroBased : LaneNumbering.OneBased;
        }
        return LaneNumbering.AssumedOneBased;
    }
}
