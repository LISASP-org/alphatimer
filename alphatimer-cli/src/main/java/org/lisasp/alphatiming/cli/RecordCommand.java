package org.lisasp.alphatiming.cli;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatiming.ui.common.AppController;
import org.lisasp.alphatiming.ui.common.AppStatus;
import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.core.recording.RecordingState;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Records into an existing project file with its stored settings until stopped, then saves it. */
@Slf4j
public final class RecordCommand {

    private final AppController controller;
    private final PrintStream out;
    private final PrintStream err;
    private final FinishedHeats finishedHeats = new FinishedHeats();
    private final CountDownLatch stopRequested = new CountDownLatch(1);
    private final CountDownLatch finished = new CountDownLatch(1);

    public RecordCommand(AppController controller, PrintStream out, PrintStream err) {
        this.controller = controller;
        this.out = out;
        this.err = err;
    }

    public ExitCode run(Path file, StopTrigger trigger) throws InterruptedException {
        try {
            return record(file, trigger);
        } finally {
            finished.countDown();
        }
    }

    public void requestStop() {
        stopRequested.countDown();
    }

    public boolean awaitFinished(Duration timeout) throws InterruptedException {
        return finished.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private ExitCode record(Path file, StopTrigger trigger) throws InterruptedException {
        if (!Files.isRegularFile(file)) {
            err.println(I18n.get("cli.fileNotFound", file));
            return ExitCode.FILE_NOT_OPENED;
        }
        controller.open(file);
        if (controller.document() == null) {
            return ExitCode.FILE_NOT_OPENED;
        }
        controller.addMessageListener(out::println);
        controller.addHeatListener(heats -> finishedHeats.describe(heats).ifPresent(out::println));
        controller.addStateListener(state -> {
            if (state != RecordingState.RUNNING) {
                requestStop();
            }
        });

        controller.startRecording();
        if (controller.session().state() == RecordingState.ERROR) {
            err.println(AppStatus.of(controller).state());
            controller.requestClose();
            return ExitCode.RECORDING_NOT_STARTED;
        }
        out.println(I18n.get("cli.recording", file.getFileName(), I18n.label(controller.document().settings().connectionType())));
        try (trigger) {
            trigger.start(this::requestStop);
            stopRequested.await();
        }

        out.println(I18n.get("cli.stopping"));
        controller.stopRecording();
        boolean saved = controller.save();
        controller.requestClose();
        if (!saved) {
            return ExitCode.NOT_SAVED;
        }
        log.info("Recording into {} ended", file.getFileName());
        out.println(I18n.get("cli.saved", file));
        return ExitCode.OK;
    }
}
