package org.lisasp.alphatiming.ui.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

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

    public static Optional<Path> chooseSavePath(Supplier<Optional<Path>> chooser, Predicate<Path> confirmOverwrite) {
        Optional<Path> chosen;
        while ((chosen = chooser.get()).isPresent()) {
            Path path = withDefaultExtension(chosen.get());
            if (!Files.exists(path) || confirmOverwrite.test(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }
}
