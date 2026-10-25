package org.lisasp.alphatiming.project.db;

import java.io.IOException;
import java.nio.file.Path;

public class ProjectFileIsFolderException extends IOException {

    private final transient Path file;

    public ProjectFileIsFolderException(Path file) {
        super("Project file is a folder: " + file);
        this.file = file;
    }

    public Path file() {
        return file;
    }
}
