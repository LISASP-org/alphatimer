package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.heats.HeatDto;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** What the Live View lists of every started heat: start, finish and the heat, newest first by these columns; an empty finish counts as the newest. */
public final class HeatRows {

    public record Row(String start, String finish, String heat) {
    }

    private static final Comparator<HeatDto> ORDER = Comparator.comparing(HeatDto::started)
                                                               .thenComparing(HeatDto::ended, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
                                                               .thenComparingInt(HeatDto::event)
                                                               .thenComparingInt(HeatDto::heat)
                                                               .reversed();

    private HeatRows() {
    }

    public static List<Row> from(Collection<HeatDto> heats, int timekeepingOffsetHours) {
        return heats.stream().filter(h -> h.started() != null).sorted(ORDER).map(h -> toRow(h, timekeepingOffsetHours)).toList();
    }

    private static Row toRow(HeatDto heat, int timekeepingOffsetHours) {
        return new Row(TimeFormat.clock(LiveTexts.timeOfDay(heat, heat.started(), timekeepingOffsetHours)),
                       heat.ended() == null ? "" : LiveTexts.endTime(heat, heat.ended(), timekeepingOffsetHours),
                       String.format(Locale.ROOT, "%02d-%03d", heat.event(), heat.heat()));
    }
}
