package org.lisasp.alphatiming.ui.core.recording;

import org.lisasp.alphatiming.ui.core.settings.ConnectionType;

public final class NoReaderForInputException extends RuntimeException {

    private final ConnectionType type;

    NoReaderForInputException(ConnectionType type) {
        super(type + " is not read as a stream");
        this.type = type;
    }

    public ConnectionType type() {
        return type;
    }
}
