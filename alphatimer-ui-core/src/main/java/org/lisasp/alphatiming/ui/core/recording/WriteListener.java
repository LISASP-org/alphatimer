package org.lisasp.alphatiming.ui.core.recording;

public interface WriteListener {

    void writeFailed(RecordedData data, Exception cause);

    /** Called on the first successful write of data after a failure, also when it happens in a later recording. */
    void writeRecovered(RecordedData data);

    /** Called when a recording stops with data that still could not be stored; that data is lost for good. */
    void dataDropped(RecordedData data, int bytes);
}
