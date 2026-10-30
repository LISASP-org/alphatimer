package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.heats.HeatDto;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What the Live View lists of every started heat: start, finish and the heat, newest first by these columns as shown (in
 * whole seconds); an empty finish counts as the newest.
 */
public final class HeatRows {

    public record Row(String start, String finish, String heat) {
    }

    private record Shown(HeatDto heat, LocalDateTime start, LocalDateTime end) {
    }

    private static final Comparator<Shown> ORDER = Comparator.comparing(Shown::start)
                                                             .thenComparing(Shown::end, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
                                                             .thenComparingInt(s -> s.heat().event())
                                                             .thenComparingInt(s -> s.heat().heat())
                                                             .reversed();

    private HeatRows() {
    }

    public static List<Row> from(Collection<HeatDto> heats, int timekeepingOffsetHours) {
        return heats.stream()
                    .filter(h -> h.started() != null)
                    .map(h -> new Shown(h, shown(h, h.started(), timekeepingOffsetHours), h.ended() == null ? null : shown(h, h.ended(), timekeepingOffsetHours)))
                    .sorted(ORDER)
                    .map(s -> toRow(s.heat(), timekeepingOffsetHours))
                    .toList();
    }

    private static LocalDateTime shown(HeatDto heat, LocalDateTime moment, int timekeepingOffsetHours) {
        return LiveTexts.shownMoment(heat, moment, timekeepingOffsetHours).truncatedTo(ChronoUnit.SECONDS);
    }

    private static Row toRow(HeatDto heat, int timekeepingOffsetHours) {
        return new Row(TimeFormat.clock(LiveTexts.timeOfDay(heat, heat.started(), timekeepingOffsetHours)),
                       heat.ended() == null ? "" : LiveTexts.endTime(heat, heat.ended(), timekeepingOffsetHours),
                       String.format(Locale.ROOT, "%02d-%03d", heat.event(), heat.heat()));
    }
}
