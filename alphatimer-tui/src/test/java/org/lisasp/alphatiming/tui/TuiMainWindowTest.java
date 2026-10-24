package org.lisasp.alphatiming.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.LiveHeats;
import org.lisasp.alphatiming.heats.Penalty;
import org.lisasp.alphatiming.ui.common.AppController;
import org.lisasp.alphatiming.ui.common.SavePrompt;
import org.lisasp.alphatiming.ui.core.project.CrashLeftover;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.DefaultSettingsStore;
import org.lisasp.basics.notification.primitive.ByteConsumer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class TuiMainWindowTest {

    @TempDir
    Path dir;

    private Locale original;
    private DefaultVirtualTerminal terminal;
    private TerminalScreen screen;
    private MultiWindowTextGUI gui;
    private AppController controller;
    private TuiMainWindow window;

    @BeforeEach
    void setUp() throws IOException {
        original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        terminal = new DefaultVirtualTerminal(new TerminalSize(150, 45));
        screen = new TerminalScreen(terminal);
        screen.startScreen();
        gui = new MultiWindowTextGUI(screen);
        controller = new AppController(Files.createDirectories(dir.resolve("work")), new DefaultSettingsStore(dir.resolve("defaults.json")), new DiscardingPrompt(), () -> s -> fakeReader(), LocalDateTime::now);
        controller.init();
        window = new TuiMainWindow(controller, new TuiSavePrompt(gui), gui);
        gui.addWindow(window);
        render();
    }

    @AfterEach
    void tearDown() throws IOException {
        window.close();
        controller.requestClose();
        controller.close();
        screen.stopScreen();
        Locale.setDefault(original);
    }

    /** A modal text dialog would wait for keys that a test never sends. */
    private static final class DiscardingPrompt implements SavePrompt {
        @Override
        public Choice askSaveChanges() {
            return Choice.DISCARD;
        }

        @Override
        public boolean askStopRecording() {
            return true;
        }

        @Override
        public Optional<Path> askSavePath() {
            return Optional.empty();
        }

        @Override
        public void showError(String title, String message) {
            throw new AssertionError(title + ": " + message);
        }

        @Override
        public RecoveryChoice askRecover(CrashLeftover leftover) {
            return RecoveryChoice.LATER;
        }

        @Override
        public boolean confirmDiscard(CrashLeftover leftover) {
            return false;
        }
    }

    private static SerialPortReader fakeReader() {
        return new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        };
    }

    private void render() throws IOException {
        gui.getGUIThread().processEventsAndUpdate();
    }

    private String screenText() {
        StringBuilder text = new StringBuilder();
        TerminalSize size = terminal.getTerminalSize();
        for (int row = 0; row < size.getRows(); row++) {
            for (int column = 0; column < size.getColumns(); column++) {
                text.append(terminal.getCharacter(column, row).getCharacterString());
            }
            text.append('\n');
        }
        return text.toString();
    }

    @Test
    void showsMenuNavigationSettingsAndStatusLikeTheGui() {
        assertThat(screenText()).contains("File", "Recording", "Settings & Recording", "Live View", "Connection type", "Stopped", "unsaved project");
    }

    @Test
    void defaultSettingsCannotBeRecordedAndNameTheMissingHost() {
        assertThat(window.settingsPanel().recordButton().isEnabled()).isFalse();
        assertThat(screenText()).contains("TCP host must not be empty");
    }

    @Test
    void recordingLocksTheSettingsUntilItIsStopped() throws IOException {
        TuiSettingsPanel settings = window.settingsPanel();
        settings.hostField().setText("localhost");
        settings.commit();
        assertThat(settings.recordButton().isEnabled()).isFalse();
        settings.competitionKeyField().setText("WK 2026");
        settings.commit();
        assertThat(controller.document().settings().tcpHost()).isEqualTo("localhost");
        assertThat(controller.document().settings().competitionKey()).isEqualTo("WK 2026");
        assertThat(settings.recordButton().isEnabled()).isTrue();

        settings.toggleRecording();
        render();

        assertThat(controller.isRecording()).isTrue();
        assertThat(settings.hostField().isEnabled()).isFalse();
        assertThat(settings.recordButton().getLabel()).isEqualTo("Stop recording");
        assertThat(screenText()).contains("Recording \u2013 connected");

        settings.toggleRecording();
        render();

        assertThat(controller.isRecording()).isFalse();
        assertThat(settings.hostField().isEnabled()).isTrue();
    }

    @Test
    void theFieldsFollowTheConnectionType() throws IOException {
        TuiSettingsPanel settings = window.settingsPanel();
        settings.connectionTypeCombo().setSelectedIndex(ConnectionType.FILE.ordinal());
        render();

        assertThat(controller.document().settings().connectionType()).isEqualTo(ConnectionType.FILE);
        assertThat(settings.fileFields().isVisible()).isTrue();
        assertThat(settings.tcpFields().isVisible()).isFalse();
    }

    @Test
    void theLiveViewShowsTheHeatWithItsLanes() throws IOException {
        window.showLiveView();
        render();
        assertThat(screenText()).contains("No data to display yet");

        HeatDto heat = new HeatDto(3, 2, HeatStatus.Finished, null, null, 0, List.of(
                new LaneDto(1, 65_430, 0, LaneStatus.Used, Penalty.None, 2, true),
                new LaneDto(2, 0, 0, LaneStatus.Used, Penalty.DidNotStart, 0, false)));
        HeatDto before = new HeatDto(3, 1, HeatStatus.Finished, null, null, 0, List.of(
                new LaneDto(1, 61_000, 0, LaneStatus.Used, Penalty.None, 1, true)));
        window.liveViewPanel().setHeats(new LiveHeats(heat, before));
        render();

        assertThat(screenText()).contains("Event 3 \u00B7 Heat 2", "1:05.43", "#2", "DNS", "Previous heat", "Event 3 \u00B7 Heat 1", "1:01.00");
    }

    @Test
    void receivedMessagesAreLoggedWithTheirTime() throws IOException {
        window.settingsPanel().appendMessage("E1 H1 L1  Finish");
        render();

        assertThat(screenText()).containsPattern("\\d\\d:\\d\\d:\\d\\d  E1 H1 L1  Finish");
    }

    @Test
    void f10ClosesAnUnchangedProjectAndTheWindow() throws IOException {
        window.handleInput(new KeyStroke(KeyType.F10));
        render();

        assertThat(controller.document()).isNull();
        assertThat(gui.getWindows()).doesNotContain(window);
    }

    @Test
    void ctrlCExitsLikeTheMenuInsteadOfKillingTheProgram() throws IOException {
        window.handleInput(new KeyStroke('c', true, false));
        render();

        assertThat(controller.document()).isNull();
    }

    @Test
    void functionKeysSwitchThePanels() throws IOException {
        window.handleInput(new KeyStroke(KeyType.F6));
        render();
        assertThat(screenText()).contains("No data to display yet");

        window.handleInput(new KeyStroke(KeyType.F5));
        render();
        assertThat(screenText()).contains("Connection type");
    }

    @Test
    void theLiveViewTellsWhenTheHeatsStartedAndFinished() throws IOException {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        window.showLiveView();
        window.liveViewPanel().setHeats(new LiveHeats(new HeatDto(3, 2, HeatStatus.Started, start.plusMinutes(3), null, 0, List.of()),
                                                      new HeatDto(3, 1, HeatStatus.Finished, start, start.plusSeconds(65), 0, List.of())));
        render();

        List<String> lines = screenText().lines().toList();
        assertCentered(lines.stream().filter(l -> l.contains("Event 3 \u00B7 Heat 2")).findFirst().orElseThrow(), "Event 3 \u00B7 Heat 2", "10:03:00 \u2192", "0:00.0");
        assertCentered(lines.stream().filter(l -> l.contains("Event 3 \u00B7 Heat 1")).findFirst().orElseThrow(), "Event 3 \u00B7 Heat 1", "10:00:00 \u2192 10:01:05", "Previous heat");
    }

    /** The times stand in the middle between the start of the title and the end of the right text. */
    private static void assertCentered(String line, String left, String middle, String right) {
        int start = line.indexOf(left);
        int end = line.lastIndexOf(right) + right.length();
        int middleStart = line.indexOf(middle);
        assertThat(middleStart).as(line).isGreaterThan(start + left.length());
        assertThat(middleStart + middle.length()).as(line).isLessThan(line.lastIndexOf(right));
        assertThat(middleStart + middle.length() / 2.0).as(line).isCloseTo((start + end) / 2.0, org.assertj.core.data.Offset.offset(1.0));
    }
}
