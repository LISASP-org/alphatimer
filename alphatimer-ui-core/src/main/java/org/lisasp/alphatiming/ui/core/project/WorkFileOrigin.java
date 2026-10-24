package org.lisasp.alphatiming.ui.core.project;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The project file a work file was last opened from or saved to, kept next to the work file so that a crash leftover can
 * name its project. Only a hint: failures are logged, never reported.
 */
@Slf4j
record WorkFileOrigin(Path projectFile, long projectLastModified) {

    private static Path besides(Path workFile) {
        return workFile.resolveSibling(workFile.getFileName() + ".project");
    }

    static void record(Path workFile, Path projectFile) {
        try {
            long lastModified = Files.getLastModifiedTime(projectFile).toMillis();
            Files.writeString(besides(workFile), projectFile.toAbsolutePath() + "\n" + lastModified + "\n", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Could not note the project file of {}", workFile.getFileName(), ex);
        }
    }

    static Optional<WorkFileOrigin> read(Path workFile) {
        Path origin = besides(workFile);
        if (!Files.exists(origin)) {
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(origin, StandardCharsets.UTF_8);
            return Optional.of(new WorkFileOrigin(Path.of(lines.get(0)), Long.parseLong(lines.get(1))));
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not read the project file of {}", workFile.getFileName(), ex);
            return Optional.empty();
        }
    }

    static void delete(Path workFile) {
        try {
            Files.deleteIfExists(besides(workFile));
        } catch (IOException ex) {
            log.warn("Could not delete the project note of {}", workFile.getFileName(), ex);
        }
    }

    boolean isUnchanged() {
        try {
            return Files.getLastModifiedTime(projectFile).toMillis() == projectLastModified;
        } catch (IOException ex) {
            return false;
        }
    }
}
