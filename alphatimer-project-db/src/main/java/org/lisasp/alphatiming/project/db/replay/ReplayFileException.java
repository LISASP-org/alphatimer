package org.lisasp.alphatiming.project.db.replay;

import java.nio.file.Path;

public class ReplayFileException extends RuntimeException {

    public enum Reason { NOT_FOUND, UNREADABLE }

    private final transient Path file;
    private final Reason reason;

    public ReplayFileException(Path file, Reason reason, Throwable cause) {
        super("Replay file " + file + ": " + reason, cause);
        this.file = file;
        this.reason = reason;
    }

    public Path file() {
        return file;
    }

    public Reason reason() {
        return reason;
    }
}
