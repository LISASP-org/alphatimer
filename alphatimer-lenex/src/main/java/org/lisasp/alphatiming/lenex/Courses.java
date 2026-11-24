package org.lisasp.alphatiming.lenex;

import java.util.Map;
import java.util.OptionalInt;

/** The pool lengths of the LENEX courses (section 5.4 of LENEX 3.0), in the unit of the event's distances. */
public final class Courses {

    private static final Map<String, Double> POOL_LENGTHS = Map.ofEntries(
            Map.entry("LCM", 50.0),
            Map.entry("SCM", 25.0),
            Map.entry("SCY", 25.0),
            Map.entry("SCM16", 50.0 / 3),
            Map.entry("SCM20", 20.0),
            Map.entry("SCM33", 100.0 / 3),
            Map.entry("SCY20", 20.0),
            Map.entry("SCY27", 27.0),
            Map.entry("SCY33", 100.0 / 3),
            Map.entry("SCY36", 110.0 / 3));

    private Courses() {
    }

    /** Empty for open water and unknown courses, which have no laps. */
    public static OptionalInt laps(String course, int distance) {
        Double length = POOL_LENGTHS.get(course);
        if (length == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) Math.max(1, Math.round(distance / length)));
    }
}
