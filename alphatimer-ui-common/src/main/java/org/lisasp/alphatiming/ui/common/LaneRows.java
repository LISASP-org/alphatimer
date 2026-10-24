package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;

import java.util.List;
import java.util.Set;

/** What the Live View shows per used lane: one time (latest intermediate or the final one), lap or penalty, place. */
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
        String detail = switch (lane.penalty()) {
            case DidNotStart -> I18n.get("lane.dns");
            case DidNotFinish -> I18n.get("lane.dnf");
            case None -> !lane.finished() && lane.lap() > 0 ? I18n.get("lane.lap", String.valueOf(lane.lap())) : "";
        };
        return new Row(lane.number(), TimeFormat.time(lane.timeInMillis()), detail, lane.place(), lane.finished());
    }
}
