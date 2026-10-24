package org.lisasp.alphatiming.ui.common;

import java.nio.file.Path;
import java.util.Locale;

public final class ProjectFiles {

    public static final String EXTENSION = "timing";
    public static final String EARLIER_EXTENSION = "heat";

    private ProjectFiles() {
    }

    public static Path withDefaultExtension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith("." + EXTENSION) || name.endsWith("." + EARLIER_EXTENSION)) {
            return file;
        }
        return file.resolveSibling(file.getFileName() + "." + EXTENSION);
    }
}
