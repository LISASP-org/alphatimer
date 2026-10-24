package org.lisasp.alphatiming.cli;

import org.lisasp.alphatiming.ui.common.SavePrompt;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;

/** Nobody can be asked during an unattended recording: changes are saved into the given file, leftovers stay for the GUI. */
public final class CliSavePrompt implements SavePrompt {

    private final Path target;
    private final PrintStream err;

    public CliSavePrompt(Path target, PrintStream err) {
        this.target = target;
        this.err = err;
    }

    @Override
    public Choice askSaveChanges() {
        return Choice.SAVE;
    }

    @Override
    public Optional<Path> askSavePath() {
        return Optional.ofNullable(target);
    }

    @Override
    public void showError(String title, String message) {
        err.println(title + ": " + message);
    }

    @Override
    public RecoveryChoice askRecover(Path workFile) {
        return RecoveryChoice.LATER;
    }
}
