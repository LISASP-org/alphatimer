package org.lisasp.alphatiming.ui.common;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.basics.jre.date.ActualDateTime;
import org.lisasp.basics.jre.date.DateTimeFacade;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
public final class LogFileCleanup {

    public static final Duration MAX_AGE = Duration.ofDays(30);
    public static final int KEEP_NEWEST = 10;

    private final Path logDir;
    private final Pattern logFileName;
    private final Duration maxAge;
    private final int keepNewest;
    private final DateTimeFacade time;

    public LogFileCleanup(Path logDir, String program, Duration maxAge, int keepNewest, DateTimeFacade time) {
        this.logDir = logDir;
        this.logFileName = Pattern.compile(Pattern.quote(program) + "-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}\\.log");
        this.maxAge = maxAge;
        this.keepNewest = keepNewest;
        this.time = time;
    }

    public static LogFileCleanup inUserHome(String program) {
        Path logDir = Path.of(System.getProperty("user.home"), ".alphatiming", "logs");
        return new LogFileCleanup(logDir, program, MAX_AGE, KEEP_NEWEST, new ActualDateTime());
    }

    public void run() {
        if (!Files.isDirectory(logDir)) {
            return;
        }
        try {
            Instant cutoff = time.now().toInstant(ZoneOffset.UTC).minus(maxAge);
            List<Path> newestFirst = logFiles().stream().sorted(Comparator.comparing(LogFileCleanup::lastModified).reversed()).toList();
            newestFirst.stream().skip(keepNewest).filter(file -> lastModified(file).toInstant().isBefore(cutoff)).forEach(this::delete);
        } catch (IOException | UncheckedIOException ex) {
            log.warn("Could not clean up the log files in {}", logDir, ex);
        }
    }

    private List<Path> logFiles() throws IOException {
        try (Stream<Path> files = Files.list(logDir)) {
            return files.filter(file -> logFileName.matcher(file.getFileName().toString()).matches()).toList();
        }
    }

    private static FileTime lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void delete(Path file) {
        try {
            Files.delete(file);
            log.debug("Deleted old log file {}", file.getFileName());
        } catch (IOException ex) {
            log.warn("Could not delete the old log file {}", file, ex);
        }
    }
}
