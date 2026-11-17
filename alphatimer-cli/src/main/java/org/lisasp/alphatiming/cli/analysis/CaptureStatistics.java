package org.lisasp.alphatiming.cli.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public final class CaptureStatistics {

    private record Group(FindingKind kind, String detail) {
    }

    private final List<AnalysedFile> files = new ArrayList<>();
    private final SortedMap<String, Long> protocolMessages = new TreeMap<>();
    private final SortedMap<String, Long> processedMessages = new TreeMap<>();
    private final SortedMap<String, Long> refinedMessages = new TreeMap<>();
    private final Map<Group, Finding> findings = new LinkedHashMap<>();

    void file(String name, long bytes) {
        files.add(new AnalysedFile(name, bytes));
    }

    void protocolMessage(String type) {
        protocolMessages.merge(type, 1L, Long::sum);
    }

    void processedMessage(String type, boolean processed) {
        processedMessages.merge(type, processed ? 1L : 0L, Long::sum);
    }

    void refinedMessage(String type) {
        refinedMessages.merge(type, 1L, Long::sum);
    }

    void finding(FindingKind kind, String detail, String file, String example) {
        findings.merge(new Group(kind, detail), new Finding(kind, detail, 1, file, example),
                       (first, next) -> new Finding(kind, detail, first.count() + 1, first.firstFile(), first.example()));
    }

    public List<AnalysedFile> files() {
        return List.copyOf(files);
    }

    public SortedMap<String, Long> protocolMessages() {
        return new TreeMap<>(protocolMessages);
    }

    /** Per data handling message type and scoreboard command (never processed); pings are never refined and have no entry. */
    public SortedMap<String, Long> processedMessages() {
        return new TreeMap<>(processedMessages);
    }

    public SortedMap<String, Long> refinedMessages() {
        return new TreeMap<>(refinedMessages);
    }

    public List<Finding> defects() {
        return findings(true);
    }

    public List<Finding> notImplemented() {
        return findings(false);
    }

    private List<Finding> findings(boolean defect) {
        return findings.values()
                       .stream()
                       .filter(f -> f.kind().isDefect() == defect)
                       .sorted(Comparator.comparingLong(Finding::count).reversed())
                       .toList();
    }
}
