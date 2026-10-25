package org.lisasp.alphatiming.cli;

public enum ExitCode {
    OK(0),
    USAGE(1),
    FILE_NOT_OPENED(2),
    RECORDING_NOT_STARTED(3),
    NOT_SAVED(4),
    CONNECTION_LOST(5);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
