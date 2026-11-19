package org.lisasp.alphatiming.cli.analysis;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatiming.cli.ExitCode;
import org.lisasp.alphatiming.ui.common.LogFileCleanup;

import java.util.List;

@Slf4j
public final class AnalyseLauncher {

    private AnalyseLauncher() {
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> log.error("Uncaught exception in thread {}", thread.getName(), throwable));
        log.info("Starting AlphaTiming raw data analysis");
        LogFileCleanup.inUserHome("alphatiming-cli").run();
        ExitCode result = new AnalyseCommand(System.out, System.err).run(List.of(args));
        System.exit(result.code());
    }
}
