package org.lisasp.alphatiming.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.alphatiming.ui.common.AppController;
import org.lisasp.alphatiming.ui.core.project.TimingDocument;
import org.lisasp.alphatiming.ui.core.settings.DefaultSettingsStore;
import org.lisasp.alphatiming.ui.core.settings.Settings;
import org.lisasp.basics.notification.primitive.ByteConsumer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RecordCommandTest {

    private static final Settings VALID = new Settings("localhost", 8585, 60, 0).withCompetitionKey("WK");

    @TempDir
    Path dir;

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ManualTrigger trigger = new ManualTrigger();
    private volatile Runnable loseConnection;
    private Locale original;
    private Path work;
    private AppController controller;
    private RecordCommand command;

    static final class ManualTrigger implements StopTrigger {
        volatile Runnable onStop;
        volatile boolean closed;

        @Override
        public void start(Runnable onStop) {
            this.onStop = onStop;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private SerialPortReader fakeReader() {
        return new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void start() {
                events.add("readerStarted");
            }

            @Override
            public void addConnectionLostListener(Runnable listener) {
                loseConnection = listener;
            }

            @Override
            public void close() {
                events.add("readerClosed");
            }
        };
    }

    @BeforeEach
    void setUp() throws IOException {
        original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        work = Files.createDirectories(dir.resolve("work"));
        controller = new AppController(work, new DefaultSettingsStore(dir.resolve("defaults.json")), new CliSavePrompt(null, new PrintStream(err, true, StandardCharsets.UTF_8)), () -> s -> fakeReader(), LocalDateTime::now);
        command = new RecordCommand(controller, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        controller.close();
        Locale.setDefault(original);
    }

    private Path project(Settings settings) throws IOException {
        Path file = dir.resolve("competition.timing");
        try (TimingDocument doc = TimingDocument.create(work, settings)) {
            doc.saveAs(file);
        }
        Files.setLastModifiedTime(file, FileTime.fromMillis(0));
        return file;
    }

    private long workFiles() throws IOException {
        try (var files = Files.list(work)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".work.sqlite")).count();
        }
    }

    @Test
    void recordsUntilTheTriggerFiresThenSavesAndCloses() throws Exception {
        Path file = project(VALID);

        Future<ExitCode> result = executor.submit(() -> command.run(file, trigger));
        await().atMost(Duration.ofSeconds(10)).until(() -> trigger.onStop != null);
        assertThat(events).containsExactly("readerStarted");
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("Recording into", "competition.timing", "Press any key to stop");

        trigger.onStop.run();

        assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(ExitCode.OK);
        assertThat(events).containsExactly("readerStarted", "readerClosed");
        assertThat(trigger.closed).isTrue();
        assertThat(Files.getLastModifiedTime(file).toMillis()).isPositive();
        assertThat(workFiles()).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("Saved");
    }

    @Test
    void aRecordingThatEndsByItselfIsSavedToo() throws Exception {
        Path file = project(VALID);

        Future<ExitCode> result = executor.submit(() -> command.run(file, trigger));
        await().atMost(Duration.ofSeconds(10)).until(() -> trigger.onStop != null);
        controller.stopRecording();

        assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(ExitCode.OK);
        assertThat(Files.getLastModifiedTime(file).toMillis()).isPositive();
    }

    @Test
    void aLostConnectionIsReportedBeforeTheStopThenSavedAndEndsWithItsOwnExitCode() throws Exception {
        Path file = project(VALID);

        Future<ExitCode> result = executor.submit(() -> command.run(file, trigger));
        await().atMost(Duration.ofSeconds(10)).until(() -> trigger.onStop != null);
        controller.addStateListener(state -> giveTheCommandTimeToPrint("Stopping"));
        loseConnection.run();

        assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(ExitCode.CONNECTION_LOST);
        assertThat(Files.getLastModifiedTime(file).toMillis()).isPositive();
        String printed = out.toString(StandardCharsets.UTF_8);
        assertThat(printed).containsSubsequence("was lost", "Stopping", "Saved");
    }

    private void giveTheCommandTimeToPrint(String text) {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (!out.toString(StandardCharsets.UTF_8).contains(text) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    @Test
    void aStopRequestedFromOutsideEndsTheRecordingLikeAKeyPress() throws Exception {
        Path file = project(VALID);

        Future<ExitCode> result = executor.submit(() -> command.run(file, trigger));
        await().atMost(Duration.ofSeconds(10)).until(() -> trigger.onStop != null);
        command.requestStop();

        assertThat(command.awaitFinished(Duration.ofSeconds(10))).isTrue();
        assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(ExitCode.OK);
    }

    @Test
    void aMissingFileIsReportedWithoutCreatingAnything() throws Exception {
        Path missing = dir.resolve("missing.timing");

        assertThat(command.run(missing, trigger)).isEqualTo(ExitCode.FILE_NOT_OPENED);

        assertThat(err.toString(StandardCharsets.UTF_8)).contains("missing.timing");
        assertThat(Files.exists(missing)).isFalse();
        assertThat(workFiles()).isZero();
        assertThat(trigger.onStop).isNull();
    }

    @Test
    void aFileThatIsNoProjectIsReported() throws Exception {
        Path file = Files.writeString(dir.resolve("notes.timing"), "not a project");

        assertThat(command.run(file, trigger)).isEqualTo(ExitCode.FILE_NOT_OPENED);

        assertThat(err.toString(StandardCharsets.UTF_8)).contains("notes.timing");
        assertThat(workFiles()).isZero();
    }

    @Test
    void invalidSettingsEndWithoutRecordingAndNameTheProblem() throws Exception {
        Path file = project(VALID.withTcp("", 8585));

        assertThat(command.run(file, trigger)).isEqualTo(ExitCode.RECORDING_NOT_STARTED);

        assertThat(events).isEmpty();
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("TCP host must not be empty");
        assertThat(workFiles()).isZero();
        assertThat(trigger.onStop).isNull();
    }
}
