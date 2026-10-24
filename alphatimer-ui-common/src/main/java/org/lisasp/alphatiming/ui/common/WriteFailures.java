package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.ui.core.recording.RecordedData;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The warning that recorded data could not be stored. It stays until every failed kind of data has been stored again and
 * the project has been saved after that, because only then is the data in the project file. Data dropped at the end of
 * a recording can never be stored again; its loss is named in the details for as long as the warning stays.
 */
final class WriteFailures {

    private record Failure(Exception cause, boolean storedAgain, long droppedBytes) {
    }

    private final StatusWarnings warnings;
    private final Map<RecordedData, Failure> failures = new EnumMap<>(RecordedData.class);

    WriteFailures(StatusWarnings warnings) {
        this.warnings = warnings;
    }

    synchronized void failed(RecordedData data, Exception cause) {
        Failure previous = failures.get(data);
        failures.put(data, new Failure(cause, false, previous == null ? 0 : previous.droppedBytes()));
        publish();
    }

    synchronized void storedAgain(RecordedData data) {
        failures.computeIfPresent(data, (d, failure) -> new Failure(failure.cause(), true, failure.droppedBytes()));
        publish();
    }

    synchronized void dropped(RecordedData data, int bytes) {
        failures.computeIfPresent(data, (d, failure) -> new Failure(failure.cause(), failure.storedAgain(), failure.droppedBytes() + bytes));
        publish();
    }

    synchronized void saved() {
        failures.values().removeIf(Failure::storedAgain);
        publish();
    }

    synchronized void reset() {
        failures.clear();
        publish();
    }

    static String describe(RecordedData data, Exception cause) {
        return I18n.get("warn.writeFailed.detail", I18n.describe(data), AppController.describe(rootCause(cause)));
    }

    static String describeDropped(RecordedData data, int bytes) {
        return I18n.get("log.dataDropped", I18n.describe(data), bytes);
    }

    private static Exception rootCause(Exception cause) {
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root instanceof Exception ex ? ex : cause;
    }

    private void publish() {
        if (failures.isEmpty()) {
            warnings.clear(StatusWarnings.Kind.RECORDING_WRITE);
            return;
        }
        String details = failures.entrySet().stream().map(e -> describe(e.getKey(), e.getValue())).collect(Collectors.joining("\n"));
        warnings.raise(StatusWarnings.Kind.RECORDING_WRITE, new StatusWarnings.Warning(I18n.get("warn.writeFailed"), details));
    }

    private static String describe(RecordedData data, Failure failure) {
        String text = describe(data, failure.cause());
        if (failure.droppedBytes() > 0) {
            text += " " + I18n.get("warn.writeFailed.dropped", failure.droppedBytes());
        }
        if (failure.storedAgain()) {
            text += " " + I18n.get("warn.writeFailed.storedAgain");
        }
        return text;
    }
}
