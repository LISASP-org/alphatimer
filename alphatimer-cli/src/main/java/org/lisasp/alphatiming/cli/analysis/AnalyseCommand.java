package org.lisasp.alphatiming.cli.analysis;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatiming.cli.ExitCode;
import org.lisasp.alphatiming.ui.common.FileFailure;
import org.lisasp.alphatiming.ui.common.I18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Analyses the recorded raw data files ({@code .dat}, {@code .serial}) directly in one directory. */
@Slf4j
public final class AnalyseCommand {

    private static final List<String> EXTENSIONS = List.of(".dat", ".serial");

    private final PrintStream out;
    private final PrintStream err;

    public AnalyseCommand(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public ExitCode run(List<String> args) {
        if (args.size() != 1 || args.getFirst().startsWith("-")) {
            err.println(I18n.get("cli.analysis.usage"));
            return ExitCode.USAGE;
        }
        Path dir = Path.of(args.getFirst()).toAbsolutePath();
        if (!Files.isDirectory(dir)) {
            err.println(I18n.get("cli.analysis.directoryNotFound", dir));
            return ExitCode.INPUT_NOT_READ;
        }
        CaptureStatistics statistics = new CaptureStatistics();
        CaptureAnalysis analysis = new CaptureAnalysis(statistics);
        Path current = dir;
        try {
            for (Path file : rawDataFiles(dir)) {
                current = file;
                try (InputStream in = Files.newInputStream(file)) {
                    analysis.analyse(file.getFileName().toString(), in);
                }
            }
        } catch (IOException ex) {
            log.warn("Could not read {}", current, ex);
            err.println(I18n.describe(FileFailure.of(ex, current)));
            return ExitCode.INPUT_NOT_READ;
        }
        new CaptureReport().lines(statistics).forEach(out::println);
        return ExitCode.OK;
    }

    private static List<Path> rawDataFiles(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(Files::isRegularFile).filter(AnalyseCommand::isRawData).sorted().toList();
        }
    }

    private static boolean isRawData(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
