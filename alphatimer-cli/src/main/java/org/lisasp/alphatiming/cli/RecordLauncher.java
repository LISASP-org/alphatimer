package org.lisasp.alphatiming.cli;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatiming.ui.common.AppController;
import org.lisasp.alphatiming.ui.common.LogFileCleanup;
import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.core.recording.RecordingSession;
import org.lisasp.alphatiming.ui.core.settings.DefaultSettingsStore;
import org.lisasp.basics.jre.date.ActualDateTime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Slf4j
public final class RecordLauncher {

    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(30);

    private RecordLauncher() {
    }

    public static void main(String[] args) throws InterruptedException {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> log.error("Uncaught exception in thread {}", thread.getName(), throwable));
        if (args.length == 1 && List.of("-h", "--help").contains(args[0])) {
            System.out.println(I18n.get("cli.usage"));
            return;
        }
        if (args.length != 1 || args[0].startsWith("-")) {
            System.err.println(I18n.get("cli.usage"));
            System.exit(ExitCode.USAGE.code());
        }
        log.info("Starting AlphaTiming command line recording");
        LogFileCleanup.inUserHome("alphatiming-cli").run();
        Path file = Path.of(args[0]).toAbsolutePath();
        Path workDir = Path.of(System.getProperty("user.home"), ".alphatiming", "work");
        AppController controller = new AppController(workDir, DefaultSettingsStore.inUserHome(), new CliSavePrompt(file, System.err), RecordingSession::standard, new ActualDateTime());
        RecordCommand command = new RecordCommand(controller, System.out, System.err);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            command.requestStop();
            try {
                command.awaitFinished(SHUTDOWN_WAIT);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "stop-recording"));
        ExitCode result = command.run(file, new TerminalStopTrigger());
        controller.close();
        System.exit(result.code());
    }
}
