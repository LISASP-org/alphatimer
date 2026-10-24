package org.lisasp.alphatiming.project.db;

import java.io.IOException;
import java.nio.file.Path;

public class ProjectFileInUseException extends IOException {

    private final transient Path file;

    public ProjectFileInUseException(Path file) {
        super("Project file is in use by another program: " + file);
        this.file = file;
    }

    public Path file() {
        return file;
    }
}
