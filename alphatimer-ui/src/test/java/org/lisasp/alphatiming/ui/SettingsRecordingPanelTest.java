package org.lisasp.alphatiming.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.alphatiming.ui.common.AppController;
import org.lisasp.alphatiming.ui.common.SavePrompt;
import org.lisasp.alphatiming.ui.core.settings.DefaultSettingsStore;
import org.lisasp.alphatiming.ui.core.settings.Settings;
import org.lisasp.basics.notification.primitive.ByteConsumer;

import javax.swing.SwingUtilities;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsRecordingPanelTest {

    @TempDir
    Path dir;

    private Locale original;
    private AppController controller;
    private SettingsRecordingPanel panel;

    @BeforeEach
    void open() throws Exception {
        original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        controller = new AppController(Files.createDirectories(dir.resolve("work")), new DefaultSettingsStore(dir.resolve("defaults.json")),
                                       new SwingSavePrompt(), () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        int serverPort = port;
        SwingUtilities.invokeAndWait(() -> {
            controller.init();
            controller.applySettings(new Settings("localhost", 8585, 60, 0).withCompetitionKey("WK").withRest(serverPort, false));
            panel = new SettingsRecordingPanel(controller);
            panel.refresh();
        });
    }

    @AfterEach
    void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            controller.stopRecording();
            controller.close();
        });
        Locale.setDefault(original);
    }

    @Test
    void startingARecordingShowsTheServerAsOn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertThat(panel.serverToggle().isSelected()).isFalse();
            assertThat(panel.serverToggle().getText()).isEqualTo("Server: off");

            panel.startRecording();
            panel.refresh();

            assertThat(controller.document().settings().restEnabled()).isTrue();
            assertThat(panel.serverToggle().isSelected()).isTrue();
            assertThat(panel.serverToggle().getText()).isEqualTo("Server: on");
            assertThat(panel.serverToggle().isEnabled()).isTrue();
        });
    }

    @Test
    void theRecordButtonShowsTheServerAsOnRightAway() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.recordToggle().doClick());

        SwingUtilities.invokeAndWait(() -> {
            assertThat(controller.isRecording()).isTrue();
            assertThat(panel.serverToggle().isSelected()).isTrue();
            assertThat(panel.serverToggle().getText()).isEqualTo("Server: on");
        });
    }

    @Test
    void theServerCanBeSwitchedOffWithTheButtonDuringARecording() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.startRecording();
            panel.refresh();

            panel.serverToggle().doClick();
            panel.refresh();

            assertThat(controller.isRecording()).isTrue();
            assertThat(controller.document().settings().restEnabled()).isFalse();
            assertThat(panel.serverToggle().getText()).isEqualTo("Server: off");
        });
    }
}
