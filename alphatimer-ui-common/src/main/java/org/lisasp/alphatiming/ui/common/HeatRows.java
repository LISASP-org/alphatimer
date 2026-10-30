package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.heats.HeatDto;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** What the Live View lists of every known heat: start, finish and the heat, ordered by these columns, empty cells last. */
public final class HeatRows {

    public record Row(String start, String finish, String heat) {
    }

    private static final Comparator<HeatDto> ORDER = Comparator.comparing(HeatDto::started, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
                                                               .thenComparing(HeatRows::finished, Comparator.nullsLast(Comparator.naturalOrder()))
                                                               .thenComparingInt(HeatDto::event)
                                                               .thenComparingInt(HeatDto::heat);

    private HeatRows() {
    }

    public static List<Row> from(Collection<HeatDto> heats, int timekeepingOffsetHours) {
        return heats.stream().sorted(ORDER).map(h -> toRow(h, timekeepingOffsetHours)).toList();
    }

    private static LocalDateTime finished(HeatDto heat) {
        return heat.started() == null ? null : heat.ended();
    }

    private static Row toRow(HeatDto heat, int timekeepingOffsetHours) {
        LocalDateTime finished = finished(heat);
        return new Row(heat.started() == null ? "" : TimeFormat.clock(LiveTexts.timeOfDay(heat, heat.started(), timekeepingOffsetHours)),
                       finished == null ? "" : TimeFormat.clock(LiveTexts.timeOfDay(heat, finished, timekeepingOffsetHours)),
                       LiveTexts.title(heat));
    }
}
