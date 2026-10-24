package org.lisasp.alphatiming.cli;

import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.LiveHeats;
import org.lisasp.alphatiming.ui.common.LiveTexts;
import org.lisasp.alphatiming.ui.common.LaneRows;

import java.util.Optional;
import java.util.Set;

/** The result of each heat as text, once, when its official end arrives. */
final class FinishedHeats {

    private String lastDescribed;

    synchronized Optional<String> describe(LiveHeats heats) {
        HeatDto heat = heats.current();
        if (heat == null || heat.status() != HeatStatus.Finished) {
            return Optional.empty();
        }
        String key = heat.competition() + "/" + heat.event() + "/" + heat.heat();
        if (key.equals(lastDescribed)) {
            return Optional.empty();
        }
        lastDescribed = key;
        StringBuilder text = new StringBuilder(LiveTexts.title(heat));
        for (LaneRows.Row row : LaneRows.from(heat, Set.of())) {
            String place = row.place() > 0 ? "#" + row.place() : "";
            text.append(System.lineSeparator()).append(String.format("%4d  %10s  %-6s %s", row.lane(), row.time(), row.detail(), place).stripTrailing());
        }
        return Optional.of(text.toString());
    }
}
