package org.lisasp.alphatiming.project.db;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The work folder ({@code ~/.alphatiming/work}) with the work files of open projects. A work file is locked while a
 * program uses it; an unlocked one was left behind by a crash.
 */
@Slf4j
public final class TimingWorkFolder {

    public static final String WORK_FILE_SUFFIX = ".work.sqlite";
    private static final String SNAPSHOT_SUFFIX = ".tmp";

    /**
     * The project file a work file was last opened from or saved to, kept next to the work file so that a crash leftover
     * can name its project. Only a hint: failures are logged, never reported.
     */
    public record Origin(Path projectFile, long projectLastModified) {

        public boolean isUnchanged() {
            try {
                return Files.getLastModifiedTime(projectFile).toMillis() == projectLastModified;
            } catch (IOException ex) {
                return false;
            }
        }
    }

    private TimingWorkFolder() {
    }

    public static Path newWorkFile(Path workDir) throws IOException {
        Files.createDirectories(workDir);
        return workDir.resolve("heat-" + UUID.randomUUID() + WORK_FILE_SUFFIX);
    }

    public static Path newSnapshotFile(Path workFile) {
        return workFile.resolveSibling(workFile.getFileName() + "." + UUID.randomUUID() + SNAPSHOT_SUFFIX);
    }

    /** The lock tells other instances that the work file belongs to a running program, so it is not a crash leftover. */
    public static LockFile lock(Path workFile) throws IOException {
        return LockFile.tryAcquire(LockFile.besides(workFile)).orElseThrow(() -> new IOException("Work file is in use: " + workFile));
    }

    /** True when a running program (this or another one) still owns the work file. */
    public static boolean isInUse(Path workFile) {
        return LockFile.isHeld(LockFile.besides(workFile));
    }

    public static List<CrashLeftover> findLeftovers(Path workDir) {
        if (!Files.isDirectory(workDir)) {
            return List.of();
        }
        removeStaleSnapshots(workDir);
        try (Stream<Path> files = Files.list(workDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(WORK_FILE_SUFFIX))
                        .filter(p -> !isInUse(p))
                        .sorted()
                        .map(TimingWorkFolder::describeLeftover)
                        .flatMap(Optional::stream)
                        .toList();
        } catch (IOException ex) {
            log.warn("Could not look for recoverable work files in {}", workDir, ex);
            return List.of();
        }
    }

    /** A snapshot of a work file in use is kept: its program may be saving right now. */
    private static void removeStaleSnapshots(Path workDir) {
        List<Path> stale;
        try (Stream<Path> files = Files.list(workDir)) {
            stale = files.filter(p -> workFileOfSnapshot(p).filter(w -> !isInUse(w)).isPresent()).toList();
        } catch (IOException ex) {
            log.warn("Could not look for stale snapshots in {}", workDir, ex);
            return;
        }
        List<String> removed = new ArrayList<>();
        for (Path snapshot : stale) {
            try {
                Files.deleteIfExists(snapshot);
                removed.add(snapshot.getFileName().toString());
            } catch (IOException ex) {
                log.warn("Could not delete the stale snapshot {}", snapshot.getFileName(), ex);
            }
        }
        if (!removed.isEmpty()) {
            log.info("Removed {} stale snapshot(s) from the work folder: {}", removed.size(), removed);
        }
    }

    private static Optional<Path> workFileOfSnapshot(Path file) {
        String name = file.getFileName().toString();
        int end = name.indexOf(WORK_FILE_SUFFIX + ".");
        if (end <= 0 || !name.endsWith(SNAPSHOT_SUFFIX)) {
            return Optional.empty();
        }
        return Optional.of(file.resolveSibling(name.substring(0, end + WORK_FILE_SUFFIX.length())));
    }

    private static Optional<CrashLeftover> describeLeftover(Path workFile) {
        try {
            return Optional.of(new CrashLeftover(workFile, Files.getLastModifiedTime(workFile).toInstant(),
                                                 origin(workFile).map(Origin::projectFile)));
        } catch (IOException ex) {
            log.warn("Could not read the work file {}", workFile.getFileName(), ex);
            return Optional.empty();
        }
    }

    /** Removes the work file with its lock file and its origin note. */
    public static void discard(Path workFile) {
        try {
            Files.deleteIfExists(workFile);
            Files.deleteIfExists(LockFile.besides(workFile));
        } catch (IOException ex) {
            log.warn("Could not delete {}", workFile, ex);
        }
        deleteOrigin(workFile);
    }

    private static Path originBesides(Path workFile) {
        return workFile.resolveSibling(workFile.getFileName() + ".project");
    }

    public static void recordOrigin(Path workFile, Path projectFile) {
        try {
            long lastModified = Files.getLastModifiedTime(projectFile).toMillis();
            Files.writeString(originBesides(workFile), projectFile.toAbsolutePath() + "\n" + lastModified + "\n", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Could not note the project file of {}", workFile.getFileName(), ex);
        }
    }

    public static Optional<Origin> origin(Path workFile) {
        Path origin = originBesides(workFile);
        if (!Files.exists(origin)) {
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(origin, StandardCharsets.UTF_8);
            return Optional.of(new Origin(Path.of(lines.get(0)), Long.parseLong(lines.get(1))));
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not read the project file of {}", workFile.getFileName(), ex);
            return Optional.empty();
        }
    }

    private static void deleteOrigin(Path workFile) {
        try {
            Files.deleteIfExists(originBesides(workFile));
        } catch (IOException ex) {
            log.warn("Could not delete the project note of {}", workFile.getFileName(), ex);
        }
    }
}
