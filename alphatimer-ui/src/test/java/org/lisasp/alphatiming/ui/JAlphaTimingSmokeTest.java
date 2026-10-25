package org.lisasp.alphatiming.ui;

import org.lisasp.alphatiming.ui.common.AppController;
import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.common.SavePrompt;
import org.lisasp.alphatiming.ui.common.TimeFormat;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.basics.notification.primitive.ByteConsumer;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.Penalty;
import org.lisasp.alphatiming.project.db.CrashLeftover;
import org.lisasp.alphatiming.ui.core.project.TimingDocument;
import org.lisasp.alphatiming.ui.core.settings.DefaultSettingsStore;
import org.lisasp.alphatiming.ui.core.settings.Settings;
import org.lisasp.alphatiming.ui.core.settings.SettingsProblem;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class JAlphaTimingSmokeTest {

    @TempDir
    Path dir;

    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    /** Releases the REST port, the work file and its lock so that the next test can start with the default settings. */
    private static void shutDown(AppController controller, JAlphaTiming frame) throws Exception {
        controller.stopRecording();
        onEdt(frame::dispose);
        TimingDocument document = controller.document();
        controller.close();
        if (document != null) {
            document.close();
        }
    }

    /** JButtonBar wraps a label that is too wide for the bar into centered HTML lines (the German texts are). */
    private static String shownText(AbstractButton button) {
        String text = button.getText();
        if (!text.startsWith("<html>")) {
            return text;
        }
        return text.replace("<br>", " ")
                   .replaceAll("<[^>]+>", "")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&");
    }

    @Test
    void layoutAndLocking() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run with xvfb-run)");

        Path work = Files.createDirectories(dir.resolve("work"));
        DefaultSettingsStore defaults = new DefaultSettingsStore(dir.resolve("defaults.json"));
        SwingSavePrompt prompt = new SwingSavePrompt();
        AppController controller = new AppController(work, defaults, prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        JAlphaTiming[] holder = new JAlphaTiming[1];
        onEdt(() -> {
            controller.init();
            holder[0] = new JAlphaTiming(controller, prompt);
            holder[0].setVisible(true);
        });
        JAlphaTiming frame = holder[0];
        try {
            onEdt(() -> {
                Component[] buttons = frame.buttonBar().getComponents();
                assertThat(buttons).hasSize(2);
                assertThat(shownText((JToggleButton) buttons[0])).isEqualTo(I18n.get("nav.settings"));
                assertThat(shownText((JToggleButton) buttons[1])).isEqualTo(I18n.get("nav.live"));
                assertThat(frame.settingsPanel().isShowing()).isTrue();
                assertThat(frame.liveViewPanel().isShowing()).isFalse();
                assertThat(frame.settingsPanel().recordToggle().isEnabled()).isFalse();
            });

            onEdt(() -> {
                assertThat(javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, frame.buttonBar())).isInstanceOf(SelectorScrollPane.class);
                assertThat(frame.liveViewPanel().getBorder()).isNotNull();
                assertThat(frame.settingsPanel().getBorder()).isNotNull();
                assertThat(javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, frame.settingsPanel())).isNull();
            });

            onEdt(() -> {
                for (int i = 0; i < 300; i++) {
                    frame.settingsPanel().appendMessage("message " + i);
                }
                frame.validate();
                SettingsRecordingPanel panel = frame.settingsPanel();
                assertThat(panel.getHeight()).isEqualTo(panel.getParent().getHeight());
                javax.swing.JScrollPane logScroller = (javax.swing.JScrollPane) javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, panel.logArea());
                assertThat(logScroller.getVerticalScrollBar().isShowing()).isTrue();
                assertThat(logScroller.getViewport().getViewSize().height).isGreaterThan(logScroller.getViewport().getExtentSize().height);
            });

            onEdt(() -> {
                assertThat(frame.pingMarker().isShowing()).isTrue();
                assertThat(frame.pingMarker().getLocationOnScreen().x).isLessThan(frame.statusStateLabel().getLocationOnScreen().x);
                assertThat(frame.pingMarker().getLocationOnScreen().y).isGreaterThan(frame.settingsPanel().getLocationOnScreen().y);
                frame.pingMarker().ping();
                assertThat(frame.pingMarker().isBright()).isTrue();
            });

            onEdt(() -> {
                frame.settingsPanel().hostField().setText("localhost");
                frame.settingsPanel().hostField().postActionEvent();
                assertThat(frame.settingsPanel().recordToggle().isEnabled()).isFalse();
                frame.settingsPanel().competitionKeyField().setText("WK 2026");
                frame.settingsPanel().competitionKeyField().postActionEvent();
                assertThat(controller.document().settings().tcpHost()).isEqualTo("localhost");
                assertThat(controller.document().settings().competitionKey()).isEqualTo("WK 2026");
                assertThat(frame.settingsPanel().recordToggle().isEnabled()).isTrue();
                assertThat(frame.settingsPanel().recordToggle().isSelected()).isFalse();
                assertThat(frame.settingsPanel().recordToggle().getText()).isEqualTo(I18n.get("recording.start"));
            });

            onEdt(() -> frame.settingsPanel().recordToggle().doClick());
            onEdt(() -> {
                assertThat(frame.settingsPanel().hostField().isEnabled()).isFalse();
                assertThat(frame.settingsPanel().recordToggle().isEnabled()).isTrue();
                assertThat(frame.settingsPanel().recordToggle().isSelected()).isTrue();
                assertThat(frame.settingsPanel().recordToggle().getText()).isEqualTo(I18n.get("recording.stop"));
                assertThat(frame.settingsPanel().lockableInputs()).isNotEmpty().allSatisfy(c -> assertThat(c.isEnabled()).as(c.getClass().getSimpleName()).isFalse());
            });

            HeatDto heat = new HeatDto(3, 2, HeatStatus.Started, LocalDateTime.now().minusSeconds(5), null, 2, List.of(
                    new LaneDto(1, 12_340, 1, LaneStatus.Used, Penalty.None, 0, false),
                    new LaneDto(2, 25_000, 2, LaneStatus.Used, Penalty.None, 1, true)));
            onEdt(() -> frame.showHeats(new org.lisasp.alphatiming.heats.LiveHeats(heat, null)));
            onEdt(() -> {
                ((JToggleButton) frame.buttonBar().getComponent(1)).doClick();
                assertThat(frame.liveViewPanel().isShowing()).isTrue();
                assertThat(frame.liveViewPanel().displayedRows()).hasSize(2);
                assertThat(frame.liveViewPanel().displayedRows().get(1).time()).isEqualTo(TimeFormat.time(25_000));
            });

            onEdt(() -> frame.settingsPanel().recordToggle().doClick());
            onEdt(() -> {
                assertThat(frame.settingsPanel().hostField().isEnabled()).isTrue();
                assertThat(frame.settingsPanel().recordToggle().isSelected()).isFalse();
                assertThat(frame.settingsPanel().recordToggle().getText()).isEqualTo(I18n.get("recording.start"));
            });
        } finally {
            shutDown(controller, frame);
        }
        assertThat(defaults.load()).isEqualTo(new Settings("localhost", 8585, 60, 1999).withCompetitionKey("WK 2026"));
    }

    @Test
    void startFromTheMenuUsesFieldsThatWereNeverCommitted() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run with xvfb-run)");
        Path work = Files.createDirectories(dir.resolve("work2"));
        SwingSavePrompt prompt = new SwingSavePrompt();
        AppController controller = new AppController(work, new DefaultSettingsStore(dir.resolve("d2.json")), prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        JAlphaTiming[] holder = new JAlphaTiming[1];
        onEdt(() -> {
            controller.init();
            holder[0] = new JAlphaTiming(controller, prompt);
            holder[0].setVisible(true);
        });
        try {
            onEdt(() -> {
                holder[0].settingsPanel().hostField().setText("typed-host");
                holder[0].settingsPanel().competitionKeyField().setText("typed-key");
                holder[0].startRecordingFromMenu();
                assertThat(controller.document().settings().tcpHost()).isEqualTo("typed-host");
                assertThat(controller.document().settings().competitionKey()).isEqualTo("typed-key");
                assertThat(controller.session().state()).isEqualTo(org.lisasp.alphatiming.ui.core.recording.RecordingState.RUNNING);
            });
        } finally {
            shutDown(controller, holder[0]);
        }
    }

    @Test
    void switchingToSerialShowsThePortChoiceAndStoresIt() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run with xvfb-run)");
        Path work = Files.createDirectories(dir.resolve("work3"));
        SwingSavePrompt prompt = new SwingSavePrompt();
        AppController controller = new AppController(work, new DefaultSettingsStore(dir.resolve("d3.json")), prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        JAlphaTiming[] holder = new JAlphaTiming[1];
        onEdt(() -> {
            controller.init();
            holder[0] = new JAlphaTiming(controller, prompt);
            holder[0].setVisible(true);
        });
        try {
            onEdt(() -> {
                SettingsRecordingPanel panel = holder[0].settingsPanel();
                assertThat(panel.hostField().isShowing()).isTrue();
                assertThat(panel.serialPortCombo().isShowing()).isFalse();
                panel.competitionKeyField().setText("WK");
                panel.competitionKeyField().postActionEvent();
                panel.connectionTypeCombo().setSelectedItem(org.lisasp.alphatiming.ui.core.settings.ConnectionType.SERIAL);
                assertThat(panel.hostField().isShowing()).isFalse();
                assertThat(panel.serialPortCombo().isShowing()).isTrue();
                assertThat(panel.recordToggle().isEnabled()).isFalse();
                assertThat(panel.recordToggle().getToolTipText()).isEqualTo(I18n.describe(SettingsProblem.SERIAL_PORT_MISSING));
                panel.serialPortCombo().setSelectedItem("/dev/ttyUSB7");
                panel.serialModeCombo().setSelectedItem(org.lisasp.alphatiming.ui.core.settings.SerialMode.QUANTUM);
                Settings s = controller.document().settings();
                assertThat(s.connectionType()).isEqualTo(org.lisasp.alphatiming.ui.core.settings.ConnectionType.SERIAL);
                assertThat(s.serialPort()).isEqualTo("/dev/ttyUSB7");
                assertThat(s.serialMode()).isEqualTo(org.lisasp.alphatiming.ui.core.settings.SerialMode.QUANTUM);
                assertThat(panel.recordToggle().isEnabled()).isTrue();
                assertThat(panel.recordToggle().getToolTipText()).isNull();
                panel.connectionTypeCombo().setSelectedItem(org.lisasp.alphatiming.ui.core.settings.ConnectionType.TCP);
                assertThat(panel.hostField().isShowing()).isTrue();
                assertThat(controller.document().settings().connectionType()).isEqualTo(org.lisasp.alphatiming.ui.core.settings.ConnectionType.TCP);
            });
        } finally {
            shutDown(controller, holder[0]);
        }
    }

    @Test
    void theServerToggleSwitchesTheServerPortOnAndOff() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run with xvfb-run)");
        Path work = Files.createDirectories(dir.resolve("work4"));
        SwingSavePrompt prompt = new SwingSavePrompt();
        AppController controller = new AppController(work, new DefaultSettingsStore(dir.resolve("d4.json")), prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        JAlphaTiming[] holder = new JAlphaTiming[1];
        onEdt(() -> {
            controller.init();
            holder[0] = new JAlphaTiming(controller, prompt);
            holder[0].setVisible(true);
        });
        try {
            onEdt(() -> {
                SettingsRecordingPanel panel = holder[0].settingsPanel();
                assertThat(panel.serverToggle().isSelected()).isTrue();
                assertThat(panel.serverPortSpinner().isEnabled()).isTrue();
                panel.serverToggle().doClick();
                assertThat(controller.document().settings().restEnabled()).isFalse();
                assertThat(panel.serverPortSpinner().isEnabled()).isFalse();
                panel.serverToggle().doClick();
                assertThat(controller.document().settings().restEnabled()).isTrue();
                assertThat(panel.serverPortSpinner().isEnabled()).isTrue();
            });
        } finally {
            shutDown(controller, holder[0]);
        }
    }

    @Test
    void theFileConnectionNeedsAReplayFileAndLocksWhileRecording() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run with xvfb-run)");
        Path work = Files.createDirectories(dir.resolve("work5"));
        Path data = dir.resolve("run.serial");
        Files.write(data, new byte[]{1, 2, 3});
        SwingSavePrompt prompt = new SwingSavePrompt();
        AppController controller = new AppController(work, new DefaultSettingsStore(dir.resolve("d5.json")), prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        JAlphaTiming[] holder = new JAlphaTiming[1];
        onEdt(() -> {
            controller.init();
            holder[0] = new JAlphaTiming(controller, prompt);
            holder[0].setVisible(true);
        });
        try {
            onEdt(() -> {
                SettingsRecordingPanel panel = holder[0].settingsPanel();
                panel.competitionKeyField().setText("WK");
                panel.competitionKeyField().postActionEvent();
                panel.connectionTypeCombo().setSelectedItem(org.lisasp.alphatiming.ui.core.settings.ConnectionType.FILE);
                assertThat(panel.hostField().isShowing()).isFalse();
                assertThat(panel.replayFileField().isShowing()).isTrue();
                assertThat(panel.recordToggle().isEnabled()).isFalse();
                panel.replayFileField().setText(data.toString());
                panel.replayFileField().postActionEvent();
                Settings s = controller.document().settings();
                assertThat(s.connectionType()).isEqualTo(org.lisasp.alphatiming.ui.core.settings.ConnectionType.FILE);
                assertThat(s.replayFile()).isEqualTo(data.toString());
                assertThat(panel.recordToggle().isEnabled()).isTrue();
                panel.recordToggle().doClick();
                assertThat(panel.lockableInputs()).allSatisfy(c -> assertThat(c.isEnabled()).as(c.getClass().getSimpleName()).isFalse());
                panel.recordToggle().doClick();
                assertThat(panel.replayFileField().isEnabled()).isTrue();
            });
        } finally {
            shutDown(controller, holder[0]);
        }
    }

    @Test
    void aFreshWindowDoesNotMarkTheProjectAsChanged() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run with xvfb-run)");
        Path work = Files.createDirectories(dir.resolve("work6"));
        SwingSavePrompt prompt = new SwingSavePrompt();
        AppController controller = new AppController(work, new DefaultSettingsStore(dir.resolve("d6.json")), prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        JAlphaTiming[] holder = new JAlphaTiming[1];
        onEdt(() -> {
            controller.init();
            holder[0] = new JAlphaTiming(controller, prompt);
            holder[0].setVisible(true);
        });
        try {
            Thread.sleep(2500); // longer than the refresh timer of the window
            onEdt(() -> {
                assertThat(controller.document().isDirty()).isFalse();
                assertThat(holder[0].getTitle()).doesNotContain("*");
            });
            assertThat(controller.requestClose()).isTrue();
        } finally {
            shutDown(controller, holder[0]);
        }
    }

    @Test
    void disposingTheWindowStopsItsTimers() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run with xvfb-run)");
        Path work = Files.createDirectories(dir.resolve("work8"));
        SwingSavePrompt prompt = new SwingSavePrompt();
        AppController controller = new AppController(work, new DefaultSettingsStore(dir.resolve("d8.json")), prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        JAlphaTiming[] holder = new JAlphaTiming[1];
        onEdt(() -> {
            controller.init();
            holder[0] = new JAlphaTiming(controller, prompt);
            holder[0].setVisible(true);
        });
        try {
            onEdt(() -> {
                assertThat(holder[0].refreshTimer().isRunning()).isTrue();
                assertThat(holder[0].liveViewPanel().clockTimer().isRunning()).isTrue();
            });
        } finally {
            shutDown(controller, holder[0]);
        }
        onEdt(() -> {
            assertThat(holder[0].refreshTimer().isRunning()).isFalse();
            assertThat(holder[0].liveViewPanel().clockTimer().isRunning()).isFalse();
        });
    }

    @Test
    void aFreshWindowWithSerialSettingsStaysCleanAndKeepsTheTypedPort() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run with xvfb-run)");
        Path work = Files.createDirectories(dir.resolve("work7"));
        DefaultSettingsStore defaults = new DefaultSettingsStore(dir.resolve("d7.json"));
        defaults.save(Settings.defaults().withConnectionType(org.lisasp.alphatiming.ui.core.settings.ConnectionType.SERIAL).withSerial("COM9", org.lisasp.alphatiming.ui.core.settings.SerialMode.QUANTUM));
        SwingSavePrompt prompt = new SwingSavePrompt();
        AppController controller = new AppController(work, defaults, prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        JAlphaTiming[] holder = new JAlphaTiming[1];
        onEdt(() -> {
            controller.init();
            holder[0] = new JAlphaTiming(controller, prompt);
            holder[0].setVisible(true);
        });
        try {
            onEdt(() -> {
                SettingsRecordingPanel panel = holder[0].settingsPanel();
                assertThat(controller.document().isDirty()).isFalse();
                panel.serialPortCombo().getEditor().setItem("COM9-typed");
                panel.serialPortCombo().getEditor().getEditorComponent().requestFocusInWindow();
                panel.refresh();
                panel.refresh();
                assertThat(panel.serialPortCombo().getEditor().getItem()).isEqualTo("COM9-typed");
                assertThat(controller.document().isDirty()).isFalse();
                assertThat(controller.document().settings().serialPort()).isEqualTo("COM9");
            });
        } finally {
            shutDown(controller, holder[0]);
        }
    }

    private static void onEdtWithin(Runnable r) throws Exception {
        FutureTask<Void> task = new FutureTask<>(r, null);
        SwingUtilities.invokeLater(task);
        task.get(5, TimeUnit.SECONDS);
    }

    private static Path savedProject(Path dir, String name) throws Exception {
        Path file = dir.resolve(name);
        try (TimingDocument saved = TimingDocument.create(Files.createDirectories(dir.resolve("saving")), Settings.defaults())) {
            saved.saveAs(file);
        }
        return file;
    }

    private static Optional<JDialog> shownDialog(String title) {
        return Arrays.stream(Window.getWindows())
                     .filter(JDialog.class::isInstance)
                     .map(JDialog.class::cast)
                     .filter(d -> d.isShowing() && title.equals(d.getTitle()))
                     .findFirst();
    }

    @Test
    void openingLoadsInTheBackgroundWhileTheWindowIsDisabled() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run with xvfb-run)");
        Path work = Files.createDirectories(dir.resolve("work9"));
        Path file = savedProject(dir, "opened.timing");
        AtomicBoolean holdNextLoad = new AtomicBoolean();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SwingSavePrompt prompt = new SwingSavePrompt();
        AppController controller = new AppController(work, new DefaultSettingsStore(dir.resolve("d9.json")), prompt, () -> {
            if (holdNextLoad.getAndSet(false)) {
                loading.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return s -> new SerialPortReader() {
                @Override
                public SerialPortReader register(ByteConsumer listener) {
                    return this;
                }

                @Override
                public void close() {
                }
            };
        }, LocalDateTime::now);
        JAlphaTiming[] holder = new JAlphaTiming[1];
        onEdt(() -> {
            controller.init();
            holder[0] = new JAlphaTiming(controller, prompt);
            holder[0].setVisible(true);
        });
        JAlphaTiming frame = holder[0];
        try {
            holdNextLoad.set(true);
            SwingUtilities.invokeLater(() -> frame.open(file));
            assertThat(loading.await(10, TimeUnit.SECONDS)).isTrue();

            onEdtWithin(() -> {
                assertThat(frame.isEnabled()).isFalse();
                assertThat(frame.getCursor().getType()).isEqualTo(Cursor.WAIT_CURSOR);
            });

            release.countDown();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> onEdt(() -> {
                assertThat(frame.isEnabled()).isTrue();
                assertThat(frame.getCursor().getType()).isEqualTo(Cursor.DEFAULT_CURSOR);
                assertThat(frame.getTitle()).contains("opened.timing");
            }));
        } finally {
            release.countDown();
            shutDown(controller, frame);
        }
    }

    @Test
    void aFailedOpenShowsTheErrorAndEnablesTheWindowAfterwards() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run with xvfb-run)");
        Path work = Files.createDirectories(dir.resolve("work10"));
        Path bad = Files.writeString(dir.resolve("bad.timing"), "not a project");
        SwingSavePrompt prompt = new SwingSavePrompt();
        AppController controller = new AppController(work, new DefaultSettingsStore(dir.resolve("d10.json")), prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        JAlphaTiming[] holder = new JAlphaTiming[1];
        onEdt(() -> {
            controller.init();
            holder[0] = new JAlphaTiming(controller, prompt);
            holder[0].setVisible(true);
        });
        JAlphaTiming frame = holder[0];
        TimingDocument before = controller.document();
        try {
            SwingUtilities.invokeLater(() -> frame.open(bad));
            String title = I18n.get("error.open.title");
            await().atMost(Duration.ofSeconds(20)).until(() -> shownDialog(title).isPresent());

            onEdtWithin(() -> assertThat(frame.isEnabled()).isFalse());

            onEdt(() -> shownDialog(title).ifPresent(JDialog::dispose));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> onEdt(() -> assertThat(frame.isEnabled()).isTrue()));
            assertThat(controller.document()).isSameAs(before);
        } finally {
            onEdt(() -> shownDialog(I18n.get("error.open.title")).ifPresent(JDialog::dispose));
            shutDown(controller, frame);
        }
    }

    private static void click(Container container, String text) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton button && text.equals(button.getText())) {
                button.doClick();
                return;
            }
            if (c instanceof Container inner) {
                click(inner, text);
            }
        }
    }

    @Test
    void openingFirstAppliesTheTypedSettingsToTheProjectBeingLeft() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run with xvfb-run)");
        Path work = Files.createDirectories(dir.resolve("work11"));
        Path file = savedProject(dir, "other.timing");
        SwingSavePrompt prompt = new SwingSavePrompt();
        AppController controller = new AppController(work, new DefaultSettingsStore(dir.resolve("d11.json")), prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        JAlphaTiming[] holder = new JAlphaTiming[1];
        onEdt(() -> {
            controller.init();
            holder[0] = new JAlphaTiming(controller, prompt);
            holder[0].setVisible(true);
        });
        JAlphaTiming frame = holder[0];
        TimingDocument before = controller.document();
        String title = I18n.get("dialog.save.title");
        try {
            onEdt(() -> {
                frame.settingsPanel().hostField().setText("typed-host");
                frame.open(file);
            });
            await().atMost(Duration.ofSeconds(10)).until(() -> shownDialog(title).isPresent());
            assertThat(before.settings().tcpHost()).isEqualTo("typed-host");

            onEdt(() -> shownDialog(title).ifPresent(d -> click(d, I18n.get("button.cancel"))));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> onEdt(() -> assertThat(frame.isEnabled()).isTrue()));
            assertThat(controller.document()).isSameAs(before);
        } finally {
            onEdt(() -> shownDialog(title).ifPresent(JDialog::dispose));
            shutDown(controller, frame);
        }
    }
    @Test
    void openingShowsTheNewSettingsInTheFocusedFieldAndKeepsTheOldTextOutOfIt() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run with xvfb-run)");
        Path work = Files.createDirectories(dir.resolve("work12"));
        Path file = dir.resolve("other-host.timing");
        try (TimingDocument saved = TimingDocument.create(Files.createDirectories(dir.resolve("saving12")), Settings.defaults().withTcp("other-host", 8585))) {
            saved.saveAs(file);
        }
        AppController controller = new AppController(work, new DefaultSettingsStore(dir.resolve("d12.json")), new DiscardingPrompt(), () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        JAlphaTiming[] holder = new JAlphaTiming[1];
        onEdt(() -> {
            controller.init();
            holder[0] = new JAlphaTiming(controller, new SwingSavePrompt());
            holder[0].setVisible(true);
        });
        JAlphaTiming frame = holder[0];
        TimingDocument before = controller.document();
        try {
            onEdt(() -> frame.settingsPanel().hostField().requestFocusInWindow());
            await().atMost(Duration.ofSeconds(10)).until(() -> onEdtTrue(() -> frame.settingsPanel().hostField().hasFocus()));
            onEdt(() -> {
                frame.settingsPanel().hostField().setText("typed-host");
                frame.open(file);
            });
            await().atMost(Duration.ofSeconds(10)).until(() -> onEdtTrue(() -> controller.document() != before && frame.isEnabled()));
            await().atMost(Duration.ofSeconds(10)).until(() -> onEdtTrue(() -> frame.settingsPanel().hostField().hasFocus()));

            onEdt(() -> {
                frame.refresh();
                assertThat(frame.settingsPanel().hostField().getText()).isEqualTo("other-host");
                frame.settingsPanel().competitionKeyField().requestFocusInWindow();
            });
            await().atMost(Duration.ofSeconds(10)).until(() -> onEdtTrue(() -> frame.settingsPanel().competitionKeyField().hasFocus()));
            onEdt(() -> {
                assertThat(controller.document().settings().tcpHost()).isEqualTo("other-host");
                assertThat(controller.document().isDirty()).isFalse();
            });
        } finally {
            shutDown(controller, frame);
        }
    }

    private static boolean onEdtTrue(BooleanSupplier condition) throws Exception {
        FutureTask<Boolean> task = new FutureTask<>(condition::getAsBoolean);
        SwingUtilities.invokeLater(task);
        return task.get(5, TimeUnit.SECONDS);
    }

    /** A Swing dialog would wait for a click that this test never sends. */
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
}
