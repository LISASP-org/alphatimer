package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.basics.notification.primitive.ByteConsumer;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.project.db.HeatStore;
import org.lisasp.alphatiming.project.db.TimingFileWriter;
import org.lisasp.alphatiming.project.db.ProjectFileInUseException;
import org.lisasp.alphatiming.heats.LiveHeats;
import org.lisasp.alphatiming.project.db.CrashLeftover;
import org.lisasp.alphatiming.project.db.TimingWorkFolder;
import org.lisasp.alphatiming.ui.core.project.TimingDocument;
import org.lisasp.alphatiming.ui.core.recording.RecordingState;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.DefaultSettingsStore;
import org.lisasp.alphatiming.ui.core.settings.SerialMode;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class AppControllerTest {

    static final class RecordingPrompt implements SavePrompt {
        final Queue<Choice> choices = new ArrayDeque<>();
        final Queue<Optional<Path>> paths = new ArrayDeque<>();
        final Queue<RecoveryChoice> recoveries = new ArrayDeque<>();
        final Queue<Boolean> discards = new ArrayDeque<>();
        final List<CrashLeftover> leftovers = new ArrayList<>();
        final Queue<Boolean> stops = new ArrayDeque<>();
        final List<String> errors = new ArrayList<>();
        final List<String> events;
        Runnable whileAsking = () -> { };

        RecordingPrompt(List<String> events) {
            this.events = events;
        }

        @Override
        public Choice askSaveChanges() {
            events.add("askSaveChanges");
            whileAsking.run();
            return choices.remove();
        }

        @Override
        public boolean askStopRecording() {
            events.add("askStopRecording");
            whileAsking.run();
            return stops.remove();
        }

        @Override
        public Optional<Path> askSavePath() {
            events.add("askSavePath");
            whileAsking.run();
            return paths.remove();
        }

        @Override
        public void showError(String title, String message) {
            errors.add(title + ": " + message);
        }

        @Override
        public RecoveryChoice askRecover(CrashLeftover leftover) {
            events.add("askRecover");
            whileAsking.run();
            leftovers.add(leftover);
            return recoveries.remove();
        }

        @Override
        public boolean confirmDiscard(CrashLeftover leftover) {
            events.add("confirmDiscard");
            return discards.remove();
        }
    }

    @TempDir
    Path dir;

    private final List<String> events = new ArrayList<>();
    private RecordingPrompt prompt;
    private DefaultSettingsStore defaults;
    private AppController controller;
    private Path work;

    private SerialPortReader fakeReader() {
        return new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {
                events.add("readerClosed");
            }
        };
    }

    @BeforeEach
    void setUp() throws IOException {
        work = Files.createDirectories(dir.resolve("work"));
        defaults = new DefaultSettingsStore(dir.resolve("defaults.json"));
        prompt = new RecordingPrompt(events);
        controller = new AppController(work, defaults, prompt, () -> s -> fakeReader(), LocalDateTime::now);
    }

    private long workFiles() throws IOException {
        try (var files = Files.list(work)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".work.sqlite")).count();
        }
    }

    private static final Settings S = new Settings("localhost", 8585, 60, 0).withCompetitionKey("WK");

    @Test
    void afterRequestCloseANewProjectCanBeOpenedAndAutoSaveScheduledAgain() {
        controller.init();
        assertThat(controller.requestClose()).isTrue();
        assertThat(controller.document()).isNull();
        controller.newProject();
        assertThat(controller.document()).isNotNull();
        controller.applySettings(controller.document().settings().withTcp("other", 1));
        assertThat(controller.document().settings().tcpHost()).isEqualTo("other");
    }

    @Test
    void pingsReachThePingListenersButNeverTheMessageLog() {
        java.util.List<ByteConsumer> consumers = new ArrayList<>();
        AppController capturing = new AppController(work, defaults, prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                consumers.add(listener);
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
        capturing.init();
        capturing.applySettings(S);
        java.util.concurrent.atomic.AtomicInteger pings = new java.util.concurrent.atomic.AtomicInteger();
        List<String> log = new ArrayList<>();
        capturing.addPingListener(pings::incrementAndGet);
        capturing.addMessageListener(log::add);
        capturing.startRecording();

        byte[] ping = {org.lisasp.alphatimer.api.ares.serial.Characters.SOH_StartOfHeader, org.lisasp.alphatimer.api.ares.serial.Characters.DC2_Periphery, 0x39,
                org.lisasp.alphatimer.api.ares.serial.Characters.DC4_Command, 0x54, 0x50, org.lisasp.alphatimer.api.ares.serial.Characters.EOT_EndOfText};
        for (byte b : ping) {
            consumers.forEach(c -> c.accept(b));
        }

        assertThat(pings.get()).isEqualTo(1);
        assertThat(log).isEmpty();
        capturing.stopRecording();
        prompt.choices.add(SavePrompt.Choice.DISCARD);
        capturing.requestClose();
    }

    @Test
    void aFreshlyStartedAppClosesWithoutAskingAndLeavesNothingBehind() throws Exception {
        controller.init();
        assertThat(controller.document().isDirty()).isFalse();
        assertThat(controller.requestClose()).isTrue();
        assertThat(events).doesNotContain("askSaveChanges", "askStopRecording");
        assertThat(workFiles()).isZero();
    }

    @Test
    void newAndOpenOnAFreshProjectDoNotAsk() throws Exception {
        controller.init();
        Path file = dir.resolve("other.heat");
        try (TimingDocument other = TimingDocument.create(work, S)) {
            other.saveAs(file);
        }
        controller.newProject();
        controller.open(file);
        assertThat(events).doesNotContain("askSaveChanges");
        assertThat(controller.document().file()).contains(file);
    }

    @Test
    void closeWhileRecordingAsksToStopThenStopsThenAsksToSave() throws Exception {
        controller.init();
        controller.applySettings(S);
        controller.startRecording();
        assertThat(controller.session().state()).isEqualTo(RecordingState.RUNNING);
        prompt.stops.add(true);
        prompt.choices.add(SavePrompt.Choice.DISCARD);

        assertThat(controller.requestClose()).isTrue();

        assertThat(events).containsExactly("askStopRecording", "readerClosed", "askSaveChanges");
        assertThat(workFiles()).isZero();
    }

    @Test
    void declinedStopKeepsTheRecordingAndTheDocumentOnCloseNewAndOpen() throws Exception {
        Path file = savedProject("other.timing");
        controller.init();
        controller.applySettings(S);
        controller.startRecording();
        TimingDocument before = controller.document();
        prompt.stops.addAll(List.of(false, false, false));

        assertThat(controller.requestClose()).isFalse();
        controller.newProject();
        controller.open(file);

        assertThat(events).containsExactly("askStopRecording", "askStopRecording", "askStopRecording");
        assertThat(controller.document()).isSameAs(before);
        assertThat(controller.session().state()).isEqualTo(RecordingState.RUNNING);
        assertThat(controller.document().isDirty()).isTrue();
        controller.stopRecording();
    }

    @Test
    void onlyChangedSettingsAreAppliedAndNeverWhileRecording() {
        controller.init();
        controller.applySettings(S);

        assertThat(controller.applySettingsIfChanged(S)).isFalse();
        assertThat(controller.applySettingsIfChanged(S.withTcp("other", 1))).isTrue();
        assertThat(controller.document().settings().tcpHost()).isEqualTo("other");

        controller.startRecording();
        assertThat(controller.isRecording()).isTrue();
        assertThat(controller.applySettingsIfChanged(S)).isFalse();
        assertThat(controller.document().settings().tcpHost()).isEqualTo("other");

        controller.stopRecording();
        assertThat(controller.isRecording()).isFalse();
    }

    @Test
    void cancelOfTheSaveQuestionAfterAConfirmedStopKeepsTheDocumentWithTheRecordingStopped() {
        controller.init();
        controller.applySettings(S);
        controller.startRecording();
        prompt.stops.add(true);
        prompt.choices.add(SavePrompt.Choice.CANCEL);

        assertThat(controller.requestClose()).isFalse();

        assertThat(controller.document()).isNotNull();
        assertThat(controller.session().state()).isEqualTo(RecordingState.STOPPED);
    }

    @Test
    void saveChoiceWritesTheFileAndClosesTheDocument() {
        controller.init();
        controller.applySettings(S);
        Path file = dir.resolve("x.heat");
        prompt.choices.add(SavePrompt.Choice.SAVE);
        prompt.paths.add(Optional.of(file));
        assertThat(controller.requestClose()).isTrue();
        assertThat(file).exists();
        assertThat(controller.document()).isNull();
    }

    @Test
    void cancelledSavePathAbortsTheClose() {
        controller.init();
        controller.applySettings(S);
        prompt.choices.add(SavePrompt.Choice.SAVE);
        prompt.paths.add(Optional.empty());
        assertThat(controller.requestClose()).isFalse();
        assertThat(controller.document()).isNotNull();
    }

    @Test
    void savedAndUnchangedDocumentClosesWithoutAsking() {
        controller.init();
        prompt.paths.add(Optional.of(dir.resolve("x.heat")));
        assertThat(controller.save()).isTrue();
        assertThat(controller.requestClose()).isTrue();
        assertThat(events).doesNotContain("askSaveChanges");
    }

    @Test
    void newProjectUsesDefaultsAndOpenBecomesTheNewDefaults() throws Exception {
        Settings remembered = new Settings("timing-pc", 9000, 30, 2000).withCompetitionKey("WK");
        defaults.save(remembered);
        controller.init();
        assertThat(controller.document().settings()).isEqualTo(remembered);

        Path file = dir.resolve("other.heat");
        try (TimingDocument other = TimingDocument.create(work, S)) {
            other.saveAs(file);
        }
        prompt.choices.add(SavePrompt.Choice.DISCARD);
        controller.open(file);

        assertThat(controller.document().settings()).isEqualTo(S);
        assertThat(defaults.load()).isEqualTo(S);
        controller.newProject();
        assertThat(controller.document().settings()).isEqualTo(S);
        assertThat(controller.document().file()).isEmpty();
    }

    @Test
    void newProjectOnDirtyDocumentCanBeCancelled() {
        controller.init();
        controller.applySettings(S);
        TimingDocument before = controller.document();
        prompt.choices.add(SavePrompt.Choice.CANCEL);
        controller.newProject();
        assertThat(controller.document()).isSameAs(before);
    }

    @Test
    void openingACorruptFileShowsAnErrorAndKeepsTheCurrentDocument() throws Exception {
        controller.init();
        TimingDocument before = controller.document();
        Path bad = dir.resolve("bad.heat");
        Files.writeString(bad, "garbage");
        prompt.choices.add(SavePrompt.Choice.DISCARD);

        controller.open(bad);

        assertThat(prompt.errors).hasSize(1);
        assertThat(controller.document()).isSameAs(before);
        assertThat(workFiles()).isEqualTo(1);
    }

    @Test
    void theErrorForACorruptFileSaysItIsNotAnAlphaTimingProject() throws Exception {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        try {
            controller.init();
            Path bad = dir.resolve("bad.timing");
            Files.writeString(bad, "garbage");
            prompt.choices.add(SavePrompt.Choice.DISCARD);

            controller.open(bad);

            assertThat(prompt.errors).singleElement().asString().contains(bad + " is not an AlphaTiming project file.");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void settingsCannotChangeWhileRecording() {
        controller.init();
        controller.applySettings(S);
        controller.startRecording();
        assertThatThrownBy(() -> controller.applySettings(S.withTcp("other", 1))).isInstanceOf(IllegalStateException.class);
        controller.stopRecording();
        controller.applySettings(S.withTcp("other", 1));
        assertThat(controller.document().settings().tcpHost()).isEqualTo("other");
        assertThat(controller.document().isDirty()).isTrue();
    }

    @Test
    void invalidSettingsMakeStartFailWithAnErrorState() {
        controller.init();
        controller.startRecording(); // defaults have blank host and key
        assertThat(controller.session().state()).isEqualTo(RecordingState.ERROR);
    }

    @Test
    void leftoverWorkFileCanBeRecoveredOrDiscarded() throws Exception {
        TimingDocument crashed = TimingDocument.create(work, S);
        crashed.database().close();
        prompt.recoveries.add(SavePrompt.RecoveryChoice.RECOVER);
        controller.init();
        assertThat(controller.document().settings()).isEqualTo(S);
        assertThat(controller.document().isDirty()).isTrue();
        prompt.choices.add(SavePrompt.Choice.DISCARD);
        controller.requestClose();
        assertThat(workFiles()).isZero();

        TimingDocument crashed2 = TimingDocument.create(work, S);
        crashed2.database().close();
        prompt.recoveries.add(SavePrompt.RecoveryChoice.DISCARD);
        prompt.discards.add(true);
        AppController second = new AppController(work, defaults, prompt, () -> s -> fakeReader(), LocalDateTime::now);
        second.init();
        assertThat(second.document().settings()).isNotEqualTo(S);
        assertThat(workFiles()).isEqualTo(1); // only the new project's work file
    }

    @Test
    void latestHeatIsRestoredWhenOpeningAFile() throws Exception {
        Path file = dir.resolve("heat.heat");
        HeatDto created;
        try (TimingDocument other = TimingDocument.create(work, S)) {
            created = new HeatStore(other.database()).create(4, 2, 8);
            other.saveAs(file);
        }
        controller.init();
        List<LiveHeats> seen = new ArrayList<>();
        controller.addHeatListener(seen::add);
        controller.open(file);
        assertThat(controller.currentHeat()).contains(created);
        assertThat(seen).containsExactly(new LiveHeats(created, null));
    }

    @Test
    void discardingALeftoverNeedsASecondConfirmation() throws Exception {
        TimingDocument crashed = TimingDocument.create(work, S);
        crashed.database().close();
        prompt.recoveries.add(SavePrompt.RecoveryChoice.DISCARD);
        prompt.discards.add(false);
        prompt.recoveries.add(SavePrompt.RecoveryChoice.LATER);
        controller.init();
        assertThat(events).containsExactly("askRecover", "confirmDiscard", "askRecover");
        assertThat(prompt.leftovers).extracting(CrashLeftover::workFile).containsOnly(crashed.database().workFile());
        assertThat(workFiles()).isEqualTo(2); // the leftover and the new project
    }

    @Test
    void aLeftoverThatCannotBeRecoveredIsOfferedAgain() throws Exception {
        Path leftover = Files.writeString(work.resolve("heat-broken" + TimingWorkFolder.WORK_FILE_SUFFIX), "not a database");
        prompt.recoveries.add(SavePrompt.RecoveryChoice.RECOVER);
        prompt.recoveries.add(SavePrompt.RecoveryChoice.DISCARD);
        prompt.discards.add(true);
        controller.init();
        assertThat(events).containsExactly("askRecover", "askRecover", "confirmDiscard");
        String[] message = I18n.get("error.recovery.message", "|").split("\\|");
        assertThat(prompt.errors).singleElement().asString()
                                 .startsWith(I18n.get("error.recovery.title") + ": " + message[0])
                                 .endsWith(message[1]);
        assertThat(prompt.leftovers).extracting(CrashLeftover::workFile).containsOnly(leftover);
        assertThat(leftover).doesNotExist();
        assertThat(controller.document()).isNotNull();
    }

    private String errorOfRecoveringALeftoverThatIsNoDatabase(Locale locale) throws IOException {
        Path leftover = Files.writeString(work.resolve("heat-broken" + TimingWorkFolder.WORK_FILE_SUFFIX), "not a database");
        prompt.recoveries.add(SavePrompt.RecoveryChoice.RECOVER);
        prompt.recoveries.add(SavePrompt.RecoveryChoice.LATER);
        Locale original = Locale.getDefault();
        Locale.setDefault(locale);
        try {
            controller.init();
        } finally {
            Locale.setDefault(original);
        }
        assertThat(prompt.errors).hasSize(1);
        return prompt.errors.getFirst().replace(leftover.toString(), "<leftover>");
    }

    @Test
    void recoveringALeftoverThatIsNoDatabaseSaysItIsDamaged() throws Exception {
        assertThat(errorOfRecoveringALeftoverThatIsNoDatabase(Locale.ENGLISH))
                .startsWith("Recovery failed: The unsaved data could not be recovered: <leftover> is damaged or not a database\n");
    }

    @Test
    void recoveringALeftoverThatIsNoDatabaseSaysItIsDamagedInGerman() throws Exception {
        assertThat(errorOfRecoveringALeftoverThatIsNoDatabase(Locale.GERMANY))
                .startsWith("Wiederherstellung fehlgeschlagen: Die ungespeicherten Daten konnten nicht wiederhergestellt werden: <leftover> ist beschädigt oder keine Datenbank\n");
    }

    private String errorOfSavingIntoAMissingFolder(Locale locale) {
        Path target = dir.resolve("missing").resolve("wk.timing");
        controller.init();
        controller.applySettings(S);
        prompt.paths.add(Optional.of(target));
        Locale original = Locale.getDefault();
        Locale.setDefault(locale);
        try {
            assertThat(controller.saveAs()).isFalse();
        } finally {
            Locale.setDefault(original);
        }
        assertThat(prompt.errors).hasSize(1);
        return prompt.errors.getFirst().replace(target.toString(), "<target>");
    }

    @Test
    void savingIntoAMissingFolderSaysTheFolderDoesNotExist() {
        assertThat(errorOfSavingIntoAMissingFolder(Locale.ENGLISH)).isEqualTo("Save project: Could not save <target>: <target> or its folder does not exist");
    }

    @Test
    void savingIntoAMissingFolderSaysTheFolderDoesNotExistInGerman() {
        assertThat(errorOfSavingIntoAMissingFolder(Locale.GERMANY))
                .isEqualTo("Projekt speichern: <target> konnte nicht gespeichert werden: <target> oder sein Ordner existiert nicht");
    }

    @Test
    void recoveryQuestionNamesTheProjectFileOfTheLeftover() throws Exception {
        Path file = dir.resolve("crashed.timing");
        try (TimingDocument crashed = TimingDocument.create(work, S)) {
            crashed.saveAs(file);
            crashed.database().close();
            prompt.recoveries.add(SavePrompt.RecoveryChoice.LATER);
            controller.init();
            assertThat(prompt.leftovers).singleElement().satisfies(l -> assertThat(l.projectFile()).contains(file));
        }
    }

    @Test
    void closingTheRecoveryDialogKeepsTheLeftoverForLater() throws Exception {
        TimingDocument crashed = TimingDocument.create(work, S);
        crashed.database().close();
        prompt.recoveries.add(SavePrompt.RecoveryChoice.LATER);
        controller.init();
        assertThat(controller.document().settings()).isNotEqualTo(S);
        assertThat(workFiles()).isEqualTo(2); // the leftover and the new project
    }

    @Test
    void openingAProjectRestoresTheCurrentAndThePreviousHeat() throws Exception {
        Path file = dir.resolve("two.heat");
        try (TimingDocument a = TimingDocument.create(work, S)) {
            HeatStore store = new HeatStore(a.database());
            store.create(1, 1, 8);
            store.create(1, 2, 8);
            a.saveAs(file);
        }
        controller.init();
        List<LiveHeats> seen = new ArrayList<>();
        controller.addHeatListener(seen::add);
        controller.open(file);
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).current().heat()).isEqualTo(2);
        assertThat(seen.get(0).previous().heat()).isEqualTo(1);
        assertThat(controller.liveHeats()).isEqualTo(seen.get(0));
    }

    @Test
    void openingAProjectWithoutHeatsClearsTheLiveHeat() throws Exception {
        Path withHeat = dir.resolve("with.heat");
        Path without = dir.resolve("without.heat");
        try (TimingDocument a = TimingDocument.create(work, S)) {
            new HeatStore(a.database()).create(4, 2, 8);
            a.saveAs(withHeat);
        }
        try (TimingDocument b = TimingDocument.create(work, S)) {
            b.saveAs(without);
        }
        controller.init();
        List<LiveHeats> seen = new ArrayList<>();
        controller.addHeatListener(seen::add);
        prompt.choices.add(SavePrompt.Choice.DISCARD);
        controller.open(withHeat);
        prompt.choices.add(SavePrompt.Choice.DISCARD);
        controller.open(without);
        assertThat(seen).hasSize(2);
        assertThat(seen.get(1)).isEqualTo(LiveHeats.NONE);
        assertThat(controller.currentHeat()).isEmpty();
    }

    @Test
    void autoSaveFailureIsVisibleUntilTheDocumentIsSavedAgain() {
        controller.init();
        controller.applySettings(S);
        List<List<StatusWarnings.Warning>> changes = new ArrayList<>();
        controller.addChangeListener(() -> changes.add(controller.warnings()));
        controller.onAutoSaveError(new IOException("disk full"));
        assertThat(controller.warnings()).singleElement().satisfies(w -> {
            assertThat(w.text()).contains("disk full");
            assertThat(w.details()).contains("disk full");
        });
        assertThat(changes).isNotEmpty();
        prompt.paths.add(Optional.of(dir.resolve("x.heat")));
        assertThat(controller.save()).isTrue();
        assertThat(controller.warnings()).isEmpty();
    }

    private void failAutoSaveOfAChangedProject() {
        controller.init();
        controller.applySettings(S);
        controller.onAutoSaveError(new IOException("disk full"));
        assertThat(controller.warnings()).hasSize(1);
        prompt.choices.add(SavePrompt.Choice.DISCARD);
    }

    @Test
    void anAutoSaveFailureOfTheDiscardedProjectIsNotShownForANewOne() {
        failAutoSaveOfAChangedProject();

        controller.newProject();

        assertThat(controller.warnings()).isEmpty();
    }

    @Test
    void anAutoSaveFailureOfTheDiscardedProjectIsNotShownForAnOpenedOne() throws Exception {
        Path other = savedProject("other.timing");
        failAutoSaveOfAChangedProject();

        controller.open(other);

        assertThat(controller.document().file()).contains(other);
        assertThat(controller.warnings()).isEmpty();
    }

    @Test
    void anAutoSaveFailureOfTheDiscardedChangesIsNotShownAfterAFailedOpenRevertsThem() throws Exception {
        Path bad = Files.writeString(dir.resolve("bad.timing"), "garbage");
        failAutoSaveOfAChangedProject();

        controller.open(bad);

        assertThat(controller.document().isDirty()).isFalse();
        assertThat(controller.warnings()).isEmpty();
    }

    private static final byte[] PING = {org.lisasp.alphatimer.api.ares.serial.Characters.SOH_StartOfHeader, org.lisasp.alphatimer.api.ares.serial.Characters.DC2_Periphery, 0x39,
            org.lisasp.alphatimer.api.ares.serial.Characters.DC4_Command, 0x54, 0x50, org.lisasp.alphatimer.api.ares.serial.Characters.EOT_EndOfText};

    private AppController recordingController(List<ByteConsumer> consumers) {
        return new AppController(work, defaults, prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                consumers.add(listener);
                return this;
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
    }

    private static void recordPing(AppController recording, List<ByteConsumer> consumers) {
        consumers.clear();
        recording.startRecording();
        for (byte b : PING) {
            consumers.forEach(c -> c.accept(b));
        }
        recording.stopRecording();
    }

    private static void execute(AppController recording, String sql) {
        recording.document().database().write(em -> em.createNativeQuery(sql).executeUpdate());
    }

    @Test
    void aWriteErrorIsVisibleWithItsDetailsUntilTheDataIsStoredAgainAndTheProjectSavedAfterwards() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        List<ByteConsumer> consumers = new CopyOnWriteArrayList<>();
        try (AppController recording = recordingController(consumers)) {
            recording.init();
            recording.applySettings(S);
            prompt.paths.add(Optional.of(dir.resolve("w.timing")));
            assertThat(recording.save()).isTrue();
            List<String> log = new CopyOnWriteArrayList<>();
            recording.addMessageListener(log::add);
            execute(recording, "CREATE TRIGGER block_rawdata BEFORE INSERT ON rawdata BEGIN SELECT RAISE(ABORT, 'disk full'); END");

            recordPing(recording, consumers);
            assertThat(recording.warnings()).singleElement().satisfies(w -> {
                assertThat(w.text()).isEqualTo("Recorded data could not be stored");
                assertThat(w.details()).startsWith("Raw data could not be stored: ").contains("disk full")
                                       .endsWith("(7 bytes were lost when the recording stopped)");
            });
            assertThat(log).anySatisfy(line -> assertThat(line).startsWith("Raw data could not be stored: ").contains("disk full"));
            assertThat(log).contains("Raw data: 7 bytes could not be stored and were lost when the recording stopped");

            assertThat(recording.save()).isTrue();
            assertThat(recording.warnings()).as("saved, but the raw data is still missing").hasSize(1);

            recordPing(recording, consumers);
            assertThat(recording.warnings()).as("losses of several recordings add up").singleElement()
                                            .satisfies(w -> assertThat(w.details()).contains("(14 bytes were lost when the recording stopped)"));

            execute(recording, "DROP TRIGGER block_rawdata");
            recordPing(recording, consumers);
            assertThat(recording.warnings()).as("stored again, but not saved yet; the lost bytes stay lost").singleElement()
                                            .satisfies(w -> assertThat(w.details()).contains("(14 bytes were lost when the recording stopped)")
                                                                                   .contains("stored again"));

            assertThat(recording.save()).isTrue();
            assertThat(recording.warnings()).isEmpty();
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void aWriteErrorOfTheClosedProjectIsNotShownForTheNextOne() {
        List<ByteConsumer> consumers = new CopyOnWriteArrayList<>();
        try (AppController recording = recordingController(consumers)) {
            recording.init();
            recording.applySettings(S);
            execute(recording, "CREATE TRIGGER block_rawdata BEFORE INSERT ON rawdata BEGIN SELECT RAISE(ABORT, 'disk full'); END");
            recordPing(recording, consumers);
            assertThat(recording.warnings()).hasSize(1);

            prompt.choices.add(SavePrompt.Choice.DISCARD);
            recording.newProject();
            assertThat(recording.warnings()).isEmpty();
        }
    }

    private static final Settings SERIAL = new Settings(ConnectionType.SERIAL, "", 1, "/dev/ttyUSB0", SerialMode.ARES21, 60, 0, false).withCompetitionKey("WK");

    private AppController losingController(List<Runnable> loseConnection) {
        return new AppController(work, defaults, prompt, () -> s -> new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void addConnectionLostListener(Runnable listener) {
                loseConnection.add(() -> new Thread(listener, "fake-connection-lost").start());
            }

            @Override
            public void close() {
            }
        }, LocalDateTime::now);
    }

    @Test
    void aLostSerialConnectionStopsTheRecordingAndWarnsUntilTheRecordingIsStartedAgain() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        List<Runnable> loseConnection = new CopyOnWriteArrayList<>();
        try (AppController recording = losingController(loseConnection)) {
            recording.init();
            recording.applySettings(SERIAL);
            List<String> log = new CopyOnWriteArrayList<>();
            recording.addMessageListener(log::add);
            recording.startRecording();

            loseConnection.getLast().run();

            await().atMost(10, TimeUnit.SECONDS).until(() -> !recording.warnings().isEmpty());
            assertThat(recording.isRecording()).isFalse();
            assertThat(recording.warnings()).singleElement().satisfies(w -> {
                assertThat(w.text()).isEqualTo("Serial connection lost, recording stopped");
                assertThat(w.details()).startsWith("The connection to serial port /dev/ttyUSB0 was lost");
                assertThat(log).contains(w.details());
            });

            recording.startRecording();
            assertThat(recording.isRecording()).isTrue();
            assertThat(recording.warnings()).isEmpty();
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void aLostTcpConnectionNamesHostAndPort() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        List<Runnable> loseConnection = new CopyOnWriteArrayList<>();
        try (AppController recording = losingController(loseConnection)) {
            recording.init();
            recording.applySettings(new Settings("timing", 9000, 60, 0).withCompetitionKey("WK"));
            recording.startRecording();

            loseConnection.getLast().run();

            await().atMost(10, TimeUnit.SECONDS).until(() -> !recording.warnings().isEmpty());
            assertThat(recording.warnings()).singleElement().satisfies(w -> {
                assertThat(w.text()).isEqualTo("Connection lost, recording stopped");
                assertThat(w.details()).startsWith("The connection to timing:9000 was lost");
            });
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void aLostConnectionOfTheClosedProjectIsNotShownForTheNextOne() {
        List<Runnable> loseConnection = new CopyOnWriteArrayList<>();
        try (AppController recording = losingController(loseConnection)) {
            recording.init();
            recording.applySettings(SERIAL);
            recording.startRecording();
            loseConnection.getLast().run();
            await().atMost(10, TimeUnit.SECONDS).until(() -> !recording.warnings().isEmpty());

            prompt.choices.add(SavePrompt.Choice.DISCARD);
            recording.newProject();
            assertThat(recording.warnings()).isEmpty();
        }
    }

    private Path savedProject(String name) throws IOException {
        Path file = dir.resolve(name);
        try (TimingDocument doc = TimingDocument.create(work, S)) {
            doc.saveAs(file);
        }
        return file;
    }

    private Path otherWork() throws IOException {
        return Files.createDirectories(dir.resolve("other-work"));
    }

    @Test
    void openingAFileThatAnotherProgramHoldsIsRefusedAndKeepsTheCurrentDocument() throws Exception {
        Path file = savedProject("x.timing");
        try (TimingDocument holder = TimingDocument.open(file, otherWork())) {
            controller.init();
            TimingDocument before = controller.document();

            controller.open(file);

            assertThat(prompt.errors).containsExactly(I18n.get("error.open.title") + ": " + I18n.get("error.open.inUse", file));
            assertThat(controller.document()).isSameAs(before);
            assertThat(workFiles()).isEqualTo(1);
        }
    }

    @Test
    void saveAsToAFileThatAnotherProgramHoldsIsRefused() throws Exception {
        Path file = savedProject("x.timing");
        try (TimingDocument holder = TimingDocument.open(file, otherWork())) {
            controller.init();
            controller.applySettings(S.withTcp("changed", 1));
            prompt.paths.add(Optional.of(file));

            assertThat(controller.saveAs()).isFalse();

            assertThat(prompt.errors).containsExactly(I18n.get("error.save.title") + ": " + I18n.get("error.saveTo.inUse", file));
            assertThat(controller.document().file()).isEmpty();
            assertThat(controller.document().isDirty()).isTrue();
        }
    }

    @Test
    void openingTheOwnFileAgainGoesBackToTheSavedStateAndKeepsTheLock() throws Exception {
        controller.init();
        controller.applySettings(S);
        Path file = dir.resolve("x.timing");
        prompt.paths.add(Optional.of(file));
        assertThat(controller.save()).isTrue();
        controller.applySettings(S.withTcp("changed", 1));
        prompt.choices.add(SavePrompt.Choice.DISCARD);

        controller.open(file);

        assertThat(prompt.errors).isEmpty();
        assertThat(controller.document().settings()).isEqualTo(S);
        assertThatThrownBy(() -> TimingDocument.open(file, otherWork())).isInstanceOf(ProjectFileInUseException.class);
        assertThat(controller.requestClose()).isTrue();
        assertThat(dir.resolve("x.timing.lock")).doesNotExist();
    }

    private void autoSaveFromAnotherThread() {
        Thread autoSave = new Thread(controller::runAutoSave);
        autoSave.start();
        try {
            autoSave.join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Path openChangedProject(String name) throws IOException {
        Path file = savedProject(name);
        controller.init();
        controller.open(file);
        controller.applySettings(S.withTcp("changed", 1));
        return file;
    }

    @Test
    void anAutoSaveDuringTheSaveQuestionDoesNotWriteChangesTheUserDiscards() throws Exception {
        Path file = openChangedProject("x.timing");
        prompt.whileAsking = this::autoSaveFromAnotherThread;
        prompt.choices.add(SavePrompt.Choice.DISCARD);

        controller.newProject();

        assertThat(TimingFileWriter.previousVersionOf(file)).doesNotExist();
        try (TimingDocument saved = TimingDocument.open(file, otherWork())) {
            assertThat(saved.settings().tcpHost()).isEqualTo(S.tcpHost());
        }
    }

    @Test
    void afterACancelledQuestionAutoSaveResumes() throws Exception {
        Path file = openChangedProject("x.timing");
        prompt.whileAsking = this::autoSaveFromAnotherThread;
        prompt.choices.add(SavePrompt.Choice.CANCEL);

        assertThat(controller.requestClose()).isFalse();
        assertThat(controller.document().isDirty()).isTrue();

        controller.runAutoSave();
        assertThat(controller.document().isDirty()).isFalse();
        assertThat(TimingFileWriter.previousVersionOf(file)).exists();
    }

    @Test
    void anAutoSaveWhileTheSaveAsDialogIsOpenDoesNotWriteTheCurrentFile() throws Exception {
        Path file = openChangedProject("a.timing");
        Path target = dir.resolve("b.timing");
        prompt.whileAsking = this::autoSaveFromAnotherThread;
        prompt.paths.add(Optional.of(target));

        assertThat(controller.saveAs()).isTrue();

        assertThat(TimingFileWriter.previousVersionOf(file)).doesNotExist();
        assertThat(target).exists();
    }

    @Test
    void discardedChangesAreNotAutoSavedWhenOpeningACorruptFileFails() throws Exception {
        Path file = openChangedProject("x.timing");
        Path bad = Files.writeString(dir.resolve("bad.timing"), "garbage");
        prompt.choices.add(SavePrompt.Choice.DISCARD);

        controller.open(bad);
        controller.runAutoSave();

        assertThat(TimingFileWriter.previousVersionOf(file)).doesNotExist();
        assertThat(prompt.errors).containsExactly(I18n.get("error.open.title") + ": " + I18n.get("error.open.invalid", bad));
        assertThat(controller.document().file()).contains(file);
        assertThat(controller.document().settings()).isEqualTo(S);
        assertThat(controller.document().isDirty()).isFalse();
        assertThat(workFiles()).isEqualTo(1);
    }

    @Test
    void discardedChangesAreNotAutoSavedWhenTheFileToOpenIsInUseAndTheOwnFileStaysLocked() throws Exception {
        Path held = savedProject("held.timing");
        try (TimingDocument holder = TimingDocument.open(held, otherWork())) {
            Path file = openChangedProject("x.timing");
            prompt.choices.add(SavePrompt.Choice.DISCARD);

            controller.open(held);
            controller.runAutoSave();

            assertThat(TimingFileWriter.previousVersionOf(file)).doesNotExist();
            assertThat(prompt.errors).containsExactly(I18n.get("error.open.title") + ": " + I18n.get("error.open.inUse", held));
            assertThat(controller.document().settings()).isEqualTo(S);
            assertThat(controller.document().isDirty()).isFalse();
            assertThatThrownBy(() -> TimingDocument.open(file, otherWork())).isInstanceOf(ProjectFileInUseException.class);
        }
    }

    @Test
    void discardedChangesOfAnUnsavedProjectAreGoneWhenTheOpenFails() throws Exception {
        controller.init();
        controller.applySettings(S);
        Path bad = Files.writeString(dir.resolve("bad.timing"), "garbage");
        prompt.choices.add(SavePrompt.Choice.DISCARD);

        controller.open(bad);

        assertThat(controller.document().settings()).isEqualTo(defaults.load());
        assertThat(controller.document().isDirty()).isFalse();
        assertThat(controller.document().file()).isEmpty();
        assertThat(workFiles()).isEqualTo(1);
    }

    @Test
    void discardedChangesAreClosedWhenNeitherTheNewProjectNorTheSavedStateCanBeLoaded() throws Exception {
        Path file = openChangedProject("x.timing");
        Files.move(work, dir.resolve("work-moved"));
        Files.writeString(work, "not a folder");
        controller.onAutoSaveError(new IOException("disk full"));
        prompt.choices.add(SavePrompt.Choice.DISCARD);

        controller.newProject();
        controller.runAutoSave();

        assertThat(controller.document()).isNull();
        assertThat(controller.warnings()).isEmpty();
        assertThat(TimingFileWriter.previousVersionOf(file)).doesNotExist();
        assertThat(prompt.errors).hasSize(2).first().asString().startsWith(I18n.get("error.new.title") + ": ");
        assertThat(prompt.errors.get(1)).startsWith(I18n.get("error.revert.title") + ": ");
        assertThat(dir.resolve("x.timing.lock")).doesNotExist();
    }
}
