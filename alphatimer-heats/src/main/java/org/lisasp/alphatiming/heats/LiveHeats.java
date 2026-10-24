package org.lisasp.alphatiming.heats;

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * What the live view shows: the heat that is being recorded and the heat that was active before it.
 *
 * @param current   null while no heat was received
 * @param previous  null until a second heat was received; holds the last state of that heat
 * @param usedLanes every lane number that was used in any heat of the project, so the view keeps a (time-less) row for a
 *                  lane that is not used in the shown heat
 */
public record LiveHeats(HeatDto current, HeatDto previous, SortedSet<Integer> usedLanes) {

    public static final LiveHeats NONE = new LiveHeats(null, null);

    public LiveHeats {
        usedLanes = Collections.unmodifiableSortedSet(new TreeSet<>(usedLanes == null ? Set.of() : usedLanes));
    }

    /** The used lanes are those of the two given heats. */
    public LiveHeats(HeatDto current, HeatDto previous) {
        this(current, previous, usedIn(current, previous));
    }

    public LiveHeats(HeatDto current, HeatDto previous, Set<Integer> usedLanes) {
        this(current, previous, new TreeSet<>(usedLanes));
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
