package org.lisasp.alphatiming.tui;

import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal;
import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatiming.ui.common.AppController;
import org.lisasp.alphatiming.ui.core.recording.RecordingSession;
import org.lisasp.alphatiming.ui.core.settings.DefaultSettingsStore;
import org.lisasp.basics.jre.date.ActualDateTime;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
public final class TuiLauncher {

    private TuiLauncher() {
    }

    public static void main(String[] args) throws IOException {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> log.error("Uncaught exception in thread {}", thread.getName(), throwable));
        log.info("Starting AlphaTiming TUI");
        // Ctrl+C must reach the window, which asks about unsaved changes, instead of ending the program on the spot.
        DefaultTerminalFactory terminals = new DefaultTerminalFactory().setUnixTerminalCtrlCBehaviour(UnixLikeTerminal.CtrlCBehaviour.TRAP);
        Screen screen = new TerminalScreen(terminals.createTerminal());
        screen.startScreen();
        try {
            MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
            TuiSavePrompt prompt = new TuiSavePrompt(gui);
            Path workDir = Path.of(System.getProperty("user.home"), ".alphatiming", "work");
            AppController controller = new AppController(workDir, DefaultSettingsStore.inUserHome(), prompt, RecordingSession::standard, new ActualDateTime());
            controller.init();
            gui.addWindowAndWait(new TuiMainWindow(controller, prompt, gui));
            controller.close();
        } finally {
            screen.stopScreen();
        }
        System.exit(0);
    }
}
