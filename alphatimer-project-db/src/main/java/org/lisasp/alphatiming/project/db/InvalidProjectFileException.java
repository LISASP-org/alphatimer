package org.lisasp.alphatiming.project.db;

import java.io.IOException;
import java.nio.file.Path;

public class InvalidProjectFileException extends IOException {

    private final transient Path file;

    public InvalidProjectFileException(Path file, Throwable cause) {
        super("Not an AlphaTiming project file: " + file, cause);
        this.file = file;
    }

    public InvalidProjectFileException(Path file) {
        this(file, null);
    }

    public Path file() {
        return file;
    }
}
