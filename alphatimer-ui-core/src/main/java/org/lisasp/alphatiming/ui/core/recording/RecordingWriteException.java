package org.lisasp.alphatiming.ui.core.recording;

final class RecordingWriteException extends RuntimeException {

    private final RecordedData data;

    RecordingWriteException(RecordedData data, RuntimeException cause) {
        super("Could not store " + data, cause);
        this.data = data;
    }

    RecordedData data() {
        return data;
    }
}
