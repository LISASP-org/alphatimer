package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.LaneTime;
import org.lisasp.alphatiming.heats.Penalty;
import org.lisasp.alphatiming.heats.TimeKind;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** What the Live View shows per used lane: the current time (the final one, else the latest intermediate one), lap or penalty, place. */
public final class LaneRows {

    public record Row(int lane, String time, String detail, int place, boolean finished) {
    }

    private LaneRows() {
    }

    /** A row for every lane used in the heat and for every lane in {@code knownLanes}; the latter stay empty when unused. */
    public static List<Row> from(HeatDto heat, Set<Integer> knownLanes) {
        if (heat == null) {
            return List.of();
        }
        return heat.lanes()
                   .stream()
                   .filter(l -> l.status() == LaneStatus.Used || knownLanes.contains(l.number()))
                   .map(l -> l.status() == LaneStatus.Used ? toRow(l) : new Row(l.number(), "", "", 0, false))
                   .toList();
    }

    private static Row toRow(LaneDto lane) {
        String detail = lane.penalties().isEmpty() ? lapText(lane) : penaltyText(lane);
        Optional<LaneTime> current = lane.current();
        int time = current.map(LaneTime::timeInMillis).orElse(0);
        return new Row(lane.number(), TimeFormat.time(time), detail, lane.place(), current.map(t -> t.kind() == TimeKind.Finish).orElse(false));
    }

    private static String lapText(LaneDto lane) {
        return lane.current()
                   .filter(t -> t.kind() == TimeKind.Split && t.lap() > 0)
                   .map(t -> I18n.get("lane.lap", String.valueOf(t.lap())))
                   .orElse("");
    }

    private static String penaltyText(LaneDto lane) {
        return EnumSet.copyOf(lane.penalties()).stream().map(LaneRows::text).collect(Collectors.joining(", "));
    }

    private static String text(Penalty penalty) {
        return switch (penalty) {
            case DidNotStart -> I18n.get("lane.dns");
            case DidNotFinish -> I18n.get("lane.dnf");
            case Disqualified -> I18n.get("lane.dsq");
        };
    }
}
