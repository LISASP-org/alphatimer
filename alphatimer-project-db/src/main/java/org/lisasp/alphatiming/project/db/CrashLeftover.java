package org.lisasp.alphatiming.project.db;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public record CrashLeftover(Path workFile, Instant lastModified, Optional<Path> projectFile) {
}
