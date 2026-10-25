package org.lisasp.alphatiming.legacy.api;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public final class AresResultFile {

    private static final Charset ARES_CHARSET = Charset.forName("windows-1252");

    private static final int EVENT = 0;
    private static final int HEAT = 2;
    private static final int LANE = 4;
    private static final int TIME = 7;
    private static final int BACKUP_TIME = 10;

    private final List<Heat> heats = new ArrayList<>();

    private AresResultFile() {
    }

    public static Heat[] read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, ARES_CHARSET).stream().filter(line -> !line.isEmpty()).toList();
        AresResultFile result = new AresResultFile();
        if (!lines.isEmpty()) {
            char separator = lines.getFirst().indexOf(',') >= 0 || lines.getFirst().indexOf(';') < 0 ? ',' : ';';
            for (String line : lines.subList(1, lines.size())) {
                result.add(CsvLine.split(line, separator));
            }
        }
        return result.heats.toArray(Heat[]::new);
    }

    private void add(List<String> row) {
        OptionalInt lane = number(row, LANE);
        OptionalInt event = number(row, EVENT);
        OptionalInt heat = number(row, HEAT);
        if (lane.isEmpty() || lane.getAsInt() < 1 || event.isEmpty() || heat.isEmpty()) {
            return;
        }
        int laneIndex = lane.getAsInt() - 1;
        int heatNumber = heat.getAsInt() + 1;
        OptionalInt backupTime = number(row, BACKUP_TIME);
        if (backupTime.isPresent() && backupTime.getAsInt() != 0) {
            store(event.getAsInt(), heatNumber, laneIndex, backupTime.getAsInt());
        }
        OptionalInt time = number(row, TIME);
        if (time.isPresent()) {
            store(event.getAsInt(), heatNumber, laneIndex, time.getAsInt());
        }
    }

    private void store(int event, int heatNumber, int laneIndex, long time) {
        Heat current = heats.isEmpty() ? null : heats.getLast();
        if (current == null || current.getEvent() != event || current.getHeat() != heatNumber) {
            current = new Heat(String.valueOf(heats.size() + 1), event, heatNumber);
            heats.add(current);
        }
        if (time > 0) {
            current.store(laneIndex, time, LaneStatus.RaceTimes);
        }
    }

    private static OptionalInt number(List<String> row, int column) {
        if (column >= row.size()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(row.get(column)));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static final class CsvLine {

        private CsvLine() {
        }

        static List<String> split(String line, char separator) {
            List<String> fields = new ArrayList<>();
            int x = 0;
            while (x < line.length()) {
                StringBuilder field = new StringBuilder();
                if (line.charAt(x) == '"') {
                    x++;
                    while (x < line.length() && line.charAt(x) != '"') {
                        field.append(line.charAt(x++));
                    }
                    while (x < line.length() && line.charAt(x) != separator) {
                        x++;
                    }
                } else {
                    while (x < line.length() && line.charAt(x) != separator) {
                        field.append(line.charAt(x++));
                    }
                }
                fields.add(field.toString().trim());
                x++;
            }
            return fields;
        }
    }
}
