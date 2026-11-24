package org.lisasp.alphatiming.heats;

import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.LaneNumbering;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * What the live view shows: the heat that is being recorded and the heat that was active before it.
 *
 * @param current   null while no heat was received
 * @param previous  null until a second heat was received; holds the last state of that heat
 * @param usedLanes every lane number that was used in any heat of the project, so the view keeps a (time-less) row for a
 *                  lane that is not used in the shown heat
 * @param known     every heat of the project with its latest state, the most recently changed first
 * @param laneNumbering where the lane numbers of the source start, as the newest used lanes said; the lanes keep the
 *                  numbers of the source, the viewer decides how to show them
 */
public record LiveHeats(HeatDto current, HeatDto previous, SortedSet<Integer> usedLanes, List<HeatDto> known, LaneNumbering laneNumbering) {

    public static final LiveHeats NONE = new LiveHeats(null, null);

    public LiveHeats {
        usedLanes = Collections.unmodifiableSortedSet(new TreeSet<>(usedLanes == null ? Set.of() : usedLanes));
        known = known == null ? List.of() : List.copyOf(known);
        laneNumbering = laneNumbering == null ? LaneNumbering.AssumedOneBased : laneNumbering;
    }

    /** The used lanes are those of the two given heats, and they are the only known heats. */
    public LiveHeats(HeatDto current, HeatDto previous) {
        this(current, previous, usedIn(current, previous));
    }

    /** The two given heats are the only known heats. */
    public LiveHeats(HeatDto current, HeatDto previous, Set<Integer> usedLanes) {
        this(current, previous, new TreeSet<>(usedLanes), Stream.of(current, previous).filter(Objects::nonNull).toList(), null);
    }

    /**
     * The most recently changed heat is the current one; the previous one is the most recently changed of the others that
     * has started, since an announced heat that never started would only hide the heat that was swum before.
     */
    public static LiveHeats of(List<HeatDto> newestFirst, Set<Integer> usedLanes) {
        return of(newestFirst, usedLanes, LaneNumbering.AssumedOneBased);
    }

    public static LiveHeats of(List<HeatDto> newestFirst, Set<Integer> usedLanes, LaneNumbering laneNumbering) {
        if (newestFirst.isEmpty()) {
            return new LiveHeats(null, null, new TreeSet<>(usedLanes), List.of(), laneNumbering);
        }
        HeatDto previous = newestFirst.stream().skip(1).filter(h -> h.status() != HeatStatus.Open).findFirst().orElse(null);
        return new LiveHeats(newestFirst.getFirst(), previous, new TreeSet<>(usedLanes), newestFirst, laneNumbering);
    }

    private static SortedSet<Integer> usedIn(HeatDto... heats) {
        SortedSet<Integer> lanes = new TreeSet<>();
        for (HeatDto heat : heats) {
            if (heat != null) {
                heat.lanes().stream().filter(l -> l.status() == LaneStatus.Used).forEach(l -> lanes.add(l.number()));
            }
        }
        return lanes;
    }
}
