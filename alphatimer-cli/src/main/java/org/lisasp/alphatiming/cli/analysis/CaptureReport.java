package org.lisasp.alphatiming.cli.analysis;

import org.lisasp.alphatiming.ui.common.I18n;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class CaptureReport {

    private static final String ENTRY = "  ";
    private static final String EXAMPLE = "      ";
    private static final String COLUMN_GAP = "  ";

    private static final String NOT_REFINED = "-";

    /** Shared by both message tables so that their columns line up. */
    private record Columns(int typeWidth, int countWidth, int processedWidth, int differenceWidth) {

        static Columns of(CaptureStatistics statistics, String countLabel, String processedLabel, String differenceLabel) {
            Map<String, Long> received = statistics.protocolMessages();
            Map<String, Long> refined = statistics.refinedMessages();
            int typeWidth = Stream.concat(received.keySet().stream(), refined.keySet().stream()).mapToInt(String::length).max().orElse(0);
            int countWidth = Stream.concat(Stream.of(countLabel),
                                           Stream.concat(received.values().stream(), refined.values().stream()).map(Columns::format))
                                   .mapToInt(String::length).max().orElse(0);
            int processedWidth = Stream.concat(Stream.of(processedLabel), statistics.processedMessages().values().stream().map(Columns::format))
                                       .mapToInt(String::length).max().orElse(0);
            int differenceWidth = Stream.concat(Stream.of(differenceLabel),
                                                statistics.processedMessages().entrySet().stream()
                                                          .map(e -> format(received.getOrDefault(e.getKey(), 0L) - e.getValue())))
                                        .mapToInt(String::length).max().orElse(0);
            return new Columns(typeWidth, countWidth, processedWidth, differenceWidth);
        }

        String header(String countLabel, String processedLabel, String differenceLabel) {
            return ENTRY + " ".repeat(typeWidth) + COLUMN_GAP + right(countLabel, countWidth) + COLUMN_GAP + right(processedLabel, processedWidth)
                    + COLUMN_GAP + right(differenceLabel, differenceWidth);
        }

        String row(String type, long count) {
            return ENTRY + type + " ".repeat(typeWidth - type.length()) + COLUMN_GAP + right(format(count), countWidth);
        }

        String row(String type, long count, Long processed) {
            return row(type, count)
                    + COLUMN_GAP + right(processed == null ? NOT_REFINED : format(processed), processedWidth)
                    + COLUMN_GAP + right(processed == null ? NOT_REFINED : format(count - processed), differenceWidth);
        }

        private static String right(String text, int width) {
            return " ".repeat(width - text.length()) + text;
        }

        private static String format(long count) {
            return NumberFormat.getIntegerInstance().format(count);
        }
    }

    List<String> lines(CaptureStatistics statistics) {
        List<String> lines = new ArrayList<>();
        List<AnalysedFile> files = statistics.files();
        lines.add(I18n.get("cli.analysis.files", files.size(), files.stream().mapToLong(AnalysedFile::bytes).sum()));
        lines.add("");
        String countLabel = I18n.get("cli.analysis.column.count");
        String processedLabel = I18n.get("cli.analysis.column.processed");
        String differenceLabel = I18n.get("cli.analysis.column.difference");
        Columns columns = Columns.of(statistics, countLabel, processedLabel, differenceLabel);
        lines.add(I18n.get("cli.analysis.protocolMessages"));
        Map<String, Long> received = statistics.protocolMessages();
        Map<String, Long> processed = statistics.processedMessages();
        if (received.isEmpty()) {
            lines.add(ENTRY + I18n.get("cli.analysis.none"));
        } else {
            lines.add(columns.header(countLabel, processedLabel, differenceLabel));
            received.forEach((type, count) -> lines.add(columns.row(type, count, processed.get(type))));
        }
        lines.add("");
        lines.add(I18n.get("cli.analysis.refinedMessages"));
        Map<String, Long> refined = statistics.refinedMessages();
        if (refined.isEmpty()) {
            lines.add(ENTRY + I18n.get("cli.analysis.none"));
        }
        refined.forEach((type, count) -> lines.add(columns.row(type, count)));
        lines.add("");
        lines.add(I18n.get("cli.analysis.defects", occurrences(statistics.defects())));
        findings(statistics.defects(), lines);
        lines.add("");
        lines.add(I18n.get("cli.analysis.notImplemented", occurrences(statistics.notImplemented())));
        findings(statistics.notImplemented(), lines);
        return lines;
    }

    private static long occurrences(List<Finding> findings) {
        return findings.stream().mapToLong(Finding::count).sum();
    }

    private static void findings(List<Finding> findings, List<String> lines) {
        if (findings.isEmpty()) {
            lines.add(ENTRY + I18n.get("cli.analysis.none"));
        }
        for (Finding f : findings) {
            lines.add(ENTRY + (f.detail().isEmpty()
                      ? I18n.get("cli.analysis.finding", f.count(), label(f.kind()))
                      : I18n.get("cli.analysis.findingWithDetail", f.count(), label(f.kind()), f.detail())));
            lines.add(EXAMPLE + I18n.get("cli.analysis.example", f.firstFile(), f.example()));
        }
    }

    private static String label(FindingKind kind) {
        return switch (kind) {
            case UNFRAMED_DATA -> I18n.get("cli.analysis.kind.unframedData");
            case INVALID_MESSAGE -> I18n.get("cli.analysis.kind.invalidMessage");
            case DAMAGED_MESSAGE -> I18n.get("cli.analysis.kind.damagedMessage");
            case MESSAGE_1_WITHOUT_MESSAGE_2 -> I18n.get("cli.analysis.kind.message1WithoutMessage2");
            case MESSAGE_2_WITHOUT_MESSAGE_1 -> I18n.get("cli.analysis.kind.message2WithoutMessage1");
            case UNKNOWN_MESSAGE -> I18n.get("cli.analysis.kind.unknownMessage");
            case NOT_REFINED -> I18n.get("cli.analysis.kind.notRefined");
        };
    }
}
