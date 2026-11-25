package org.lisasp.alphatimer.api.refinedmessages.accepted;

import lombok.Value;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.LaneNumbering;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

@Value
public class UsedLanesMessage implements RefinedMessage {
    private final LocalDateTime timestamp;
    private final String competition;
    private final short event;
    private final byte heat;
    private final String round;
    /** the numbers of the used lanes as the source numbers them */
    private final SortedSet<Integer> lanes;
    private final LaneNumbering numbering;

    public UsedLanesMessage(LocalDateTime timestamp, String competition, short event, byte heat, String round, Collection<Integer> lanes,
                            LaneNumbering numbering) {
        this.timestamp = timestamp;
        this.competition = competition;
        this.event = event;
        this.heat = heat;
        this.round = round;
        this.lanes = Collections.unmodifiableSortedSet(new TreeSet<>(lanes));
        this.numbering = numbering;
    }

    /** The used lanes of ARES: one flag per lane 1 to 10, {@code '1'} for a used lane. */
    public UsedLanesMessage(LocalDateTime timestamp, String competition, short event, byte heat, String usedLanes) {
        this(timestamp, competition, event, heat, "", aresLanes(usedLanes), LaneNumbering.OneBased);
    }

    private static SortedSet<Integer> aresLanes(String usedLanes) {
        if (!usedLanes.matches("[01]{10}")) {
            throw new InvalidUsedLanesException(usedLanes);
        }
        SortedSet<Integer> lanes = new TreeSet<>();
        for (int i = 0; i < usedLanes.length(); i++) {
            if (usedLanes.charAt(i) == '1') {
                lanes.add(i + 1);
            }
        }
        return lanes;
    }
}
