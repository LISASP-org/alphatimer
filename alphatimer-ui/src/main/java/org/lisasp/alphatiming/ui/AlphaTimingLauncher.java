package org.lisasp.alphatiming.ui;

import org.lisasp.alphatiming.ui.common.AppController;
import org.lisasp.alphatiming.ui.common.LogFileCleanup;
import de.df.jutils.gui.util.DesignInit;
import org.lisasp.basics.jre.date.ActualDateTime;
import org.lisasp.alphatiming.ui.core.recording.RecordingSession;
import org.lisasp.alphatiming.ui.core.settings.DefaultSettingsStore;
import org.lisasp.swing.filechooser.platform.PlatformFileChooser;

import lombok.extern.slf4j.Slf4j;

import javax.swing.SwingUtilities;
import java.nio.file.Path;

@Slf4j
public final class AlphaTimingLauncher {

    private AlphaTimingLauncher() {
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> log.error("Uncaught exception in thread {}", thread.getName(), throwable));
        log.info("Starting AlphaTiming");
        LogFileCleanup.inUserHome("alphatiming").run();
        DesignInit.init();
        PlatformFileChooser.initialize();
        SwingUtilities.invokeLater(() -> {
            Path workDir = Path.of(System.getProperty("user.home"), ".alphatiming", "work");
            SwingSavePrompt prompt = new SwingSavePrompt();
            AppController controller = new AppController(workDir, DefaultSettingsStore.inUserHome(), prompt, RecordingSession::standard, new ActualDateTime());
            controller.init();
            new JAlphaTiming(controller, prompt).setVisible(true);
        });
    }
}
