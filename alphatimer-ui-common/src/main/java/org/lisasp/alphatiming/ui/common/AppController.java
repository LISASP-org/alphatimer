package org.lisasp.alphatiming.ui.common;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.alphatiming.project.db.InvalidProjectFileException;
import org.lisasp.alphatiming.project.db.ProjectFileInUseException;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.project.db.HeatStore;
import org.lisasp.alphatiming.heats.LiveHeats;
import org.lisasp.alphatiming.ui.core.project.AutoSaver;
import org.lisasp.alphatiming.project.db.CrashLeftover;
import org.lisasp.alphatiming.project.db.TimingWorkFolder;
import org.lisasp.alphatiming.ui.core.project.TimingDocument;
import org.lisasp.alphatiming.ui.core.recording.LostConnection;
import org.lisasp.alphatiming.ui.core.recording.RecordedData;
import org.lisasp.alphatiming.ui.core.recording.RecordingSession;
import org.lisasp.alphatiming.ui.core.recording.RecordingState;
import org.lisasp.alphatiming.ui.core.recording.WriteListener;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.DefaultSettingsStore;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Contains no Swing code; listeners are called from whatever thread caused the change. */
@Slf4j
public final class AppController implements AutoCloseable {

    private final Path workDir;
    private final DefaultSettingsStore defaults;
    private final SavePrompt prompt;
    private final Supplier<RecordingSession.ReaderFactory> readers;
    private final DateTimeFacade time;
    private final AutoSaver autoSaver;

    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<LiveHeats>> heatListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<RecordingState>> stateListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> messageListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> pingListeners = new CopyOnWriteArrayList<>();

    private volatile TimingDocument document;
    private volatile RecordingSession session;
    private volatile LiveHeats lastHeats = LiveHeats.NONE;
    private final StatusWarnings warnings = new StatusWarnings();
    private final WriteFailures writeFailures = new WriteFailures(warnings);

    public AppController(Path workDir, DefaultSettingsStore defaults, SavePrompt prompt, Supplier<RecordingSession.ReaderFactory> readers, DateTimeFacade time) {
        this.workDir = workDir;
        this.defaults = defaults;
        this.prompt = prompt;
        this.readers = readers;
        this.time = time;
        this.autoSaver = new AutoSaver(() -> document, this::onAutoSaveError);
    }

    public void init() {
        for (CrashLeftover leftover : TimingWorkFolder.findLeftovers(workDir)) {
            if (document != null) {
                break; // keep the remaining leftovers for the next start
            }
            withoutAutoSave(() -> offerRecovery(leftover));
        }
        if (document == null) {
            newProject();
        }
    }

    private void offerRecovery(CrashLeftover leftover) {
        while (true) {
            switch (prompt.askRecover(leftover)) {
                case RECOVER -> {
                    try {
                        install(TimingDocument.recover(leftover.workFile()));
                        return;
                    } catch (IOException | RuntimeException ex) {
                        log.warn("Could not recover {}", leftover.workFile().getFileName(), ex);
                        prompt.showError(I18n.get("error.recovery.title"), I18n.get("error.recovery.message", describe(ex, leftover.workFile())));
                    }
                }
                case DISCARD -> {
                    if (prompt.confirmDiscard(leftover)) {
                        TimingWorkFolder.discard(leftover.workFile());
                        return;
                    }
                }
                case LATER -> {
                    return;
                }
            }
        }
    }

    public void newProject() {
        withoutAutoSave(() -> {
            Leaving leaving = prepareToLeaveDocument();
            if (leaving == Leaving.CANCELLED) {
                return;
            }
            try {
                replaceDocument(TimingDocument.create(workDir, defaults.load()));
            } catch (IOException ex) {
                log.warn("Could not create a new project", ex);
                prompt.showError(I18n.get("error.new.title"), I18n.get("error.new.message", describe(ex, workDir)));
                revertIfDiscarded(leaving);
            }
        });
    }

    public void open(Path file) {
        withoutAutoSave(() -> openAfterQuestions(file));
    }

    private void openAfterQuestions(Path file) {
        Leaving leaving = prepareToLeaveDocument();
        if (leaving == Leaving.CANCELLED) {
            return;
        }
        TimingDocument current = document;
        try {
            TimingDocument opened = current != null && current.isSavedAs(file) ? current.reopen(workDir) : TimingDocument.open(file, workDir);
            defaults.save(opened.settings());
            replaceDocument(opened);
            return;
        } catch (ProjectFileInUseException ex) {
            prompt.showError(I18n.get("error.open.title"), I18n.get("error.open.inUse", file));
        } catch (InvalidProjectFileException ex) {
            prompt.showError(I18n.get("error.open.title"), I18n.get("error.open.invalid", file));
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not open {}", file.getFileName(), ex);
            prompt.showError(I18n.get("error.open.title"), I18n.get("error.open.message", file, describe(ex, file)));
        }
        revertIfDiscarded(leaving);
    }

    /** Without this, the next auto-save or Save would write the changes the user chose to discard. */
    private void revertIfDiscarded(Leaving leaving) {
        TimingDocument discarded = document;
        if (leaving != Leaving.CHANGES_DISCARDED || discarded == null) {
            return;
        }
        try {
            replaceDocument(discarded.file().isPresent() ? discarded.reopen(workDir) : TimingDocument.create(workDir, defaults.load()));
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not load the saved state after discarding changes", ex);
            prompt.showError(I18n.get("error.revert.title"), I18n.get("error.revert.message", describe(ex, discarded.file().orElse(workDir))));
            closeDocument();
            fireChange();
        }
    }

    public boolean save() {
        TimingDocument doc = document;
        if (doc == null) {
            return false;
        }
        Optional<Path> file = doc.file();
        if (file.isEmpty()) {
            return saveAs();
        }
        try {
            doc.save();
            defaults.save(doc.settings());
            fireChange();
            return true;
        } catch (IOException ex) {
            log.warn("Could not save {}", file.get().getFileName(), ex);
            prompt.showError(I18n.get("error.save.title"), I18n.get("error.save.message", describe(ex, file.get())));
            return false;
        }
    }

    public boolean saveAs() {
        return withoutAutoSave(this::askPathAndSave);
    }

    private boolean askPathAndSave() {
        TimingDocument doc = document;
        if (doc == null) {
            return false;
        }
        Optional<Path> target = prompt.askSavePath();
        if (target.isEmpty()) {
            return false;
        }
        try {
            doc.saveAs(target.get());
            defaults.save(doc.settings());
            fireChange();
            return true;
        } catch (ProjectFileInUseException ex) {
            prompt.showError(I18n.get("error.save.title"), I18n.get("error.saveTo.inUse", target.get()));
            return false;
        } catch (IOException ex) {
            log.warn("Could not save {}", target.get().getFileName(), ex);
            prompt.showError(I18n.get("error.save.title"), I18n.get("error.saveTo.message", target.get(), describe(ex, target.get())));
            return false;
        }
    }

    /** Asks to stop a running recording, then about unsaved data, and closes the document. True when the program may exit. */
    public boolean requestClose() {
        return withoutAutoSave(() -> {
            if (prepareToLeaveDocument() == Leaving.CANCELLED) {
                return false;
            }
            closeDocument();
            return true;
        });
    }

    private void closeDocument() {
        TimingDocument doc = document;
        RecordingSession s = session;
        document = null;
        session = null;
        if (s != null) {
            s.close();
        }
        clearWarningsOfTheDocument();
        if (doc != null) {
            doc.close();
        }
    }

    public void startRecording() {
        TimingDocument doc = document;
        RecordingSession s = session;
        if (doc == null || s == null) {
            return;
        }
        Settings settings = doc.settings();
        defaults.save(settings);
        warnings.clear(StatusWarnings.Kind.CONNECTION_LOST);
        s.start(settings);
    }

    public void stopRecording() {
        RecordingSession s = session;
        if (s != null) {
            s.stop();
        }
    }

    public void applySettings(Settings settings) {
        TimingDocument doc = document;
        RecordingSession s = session;
        if (doc == null) {
            return;
        }
        if (s != null && s.state() == RecordingState.RUNNING) {
            throw new IllegalStateException("Settings cannot be changed while recording.");
        }
        doc.updateSettings(settings);
        scheduleAutoSave(settings);
        if (s != null) {
            serve(s, settings);
        }
        fireChange();
    }

    /** The server runs whenever it is switched on, with or without a recording. */
    private void serve(RecordingSession s, Settings settings) {
        try {
            s.serve(settings.restEnabled(), settings.restPort());
            warnings.clear(StatusWarnings.Kind.SERVER);
        } catch (IOException ex) {
            log.warn("Could not start the server on port {}", settings.restPort(), ex);
            StatusWarnings.Warning warning = new StatusWarnings.Warning(I18n.get("warn.serverPort", String.valueOf(settings.restPort())),
                                                                        I18n.get("warn.serverPort.detail", String.valueOf(settings.restPort())));
            warnings.raise(StatusWarnings.Kind.SERVER, warning);
            announce(warning.details());
        }
    }

    /** Applies settings entered in a form unless they are unchanged or a recording runs; true when they were applied. */
    public boolean applySettingsIfChanged(Settings settings) {
        TimingDocument doc = document;
        if (doc == null || isRecording() || settings.equals(doc.settings())) {
            return false;
        }
        applySettings(settings);
        return true;
    }

    public boolean isRecording() {
        RecordingSession s = session;
        return s != null && s.state() == RecordingState.RUNNING;
    }

    public TimingDocument document() {
        return document;
    }

    public RecordingSession session() {
        return session;
    }

    /** The problems the user should see until they are resolved, the most important first. */
    /** Recorded data that is not in the project file even after saving; the write warning stays until it is. */
    public boolean recordedDataMissing() {
        return warnings.isActive(StatusWarnings.Kind.RECORDING_WRITE);
    }

    public List<StatusWarnings.Warning> warnings() {
        return warnings.active();
    }

    void onAutoSaveError(Exception ex) {
        TimingDocument doc = document;
        String text = I18n.get("warn.autoSaveFailed", describe(ex, doc == null ? workDir : doc.file().orElse(workDir)));
        warnings.raise(StatusWarnings.Kind.AUTO_SAVE, new StatusWarnings.Warning(text, text));
        announce(text);
        fireChange();
    }

    private void onConnectionLost(LostConnection lost) {
        StatusWarnings.Warning warning = lost.type() == ConnectionType.SERIAL
                ? new StatusWarnings.Warning(I18n.get("warn.connectionLost"), I18n.get("warn.connectionLost.detail", lost.address()))
                : new StatusWarnings.Warning(I18n.get("warn.connectionLost.other"), I18n.get("warn.connectionLost.other.detail", lost.address()));
        warnings.raise(StatusWarnings.Kind.CONNECTION_LOST, warning);
        announce(warning.details());
        fireChange();
    }

    public LiveHeats liveHeats() {
        return lastHeats;
    }

    public int timekeepingOffsetHours() {
        TimingDocument doc = document;
        return doc == null ? 0 : doc.settings().timekeepingOffsetHours();
    }

    public Optional<HeatDto> currentHeat() {
        return Optional.ofNullable(lastHeats.current());
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void addHeatListener(Consumer<LiveHeats> listener) {
        heatListeners.add(listener);
    }

    public void addStateListener(Consumer<RecordingState> listener) {
        stateListeners.add(listener);
    }

    public void addPingListener(Runnable listener) {
        pingListeners.add(listener);
    }

    public void addMessageListener(Consumer<String> listener) {
        messageListeners.add(listener);
    }

    @Override
    public void close() {
        autoSaver.close();
    }

    void runAutoSave() {
        autoSaver.runOnce();
    }

    /** The user's answers decide whether changes reach the file, so the pause lasts until a discarded document is closed. */
    private <T> T withoutAutoSave(Supplier<T> questionsAndTheirConsequence) {
        autoSaver.pause();
        try {
            return questionsAndTheirConsequence.get();
        } finally {
            autoSaver.resume();
        }
    }

    private void withoutAutoSave(Runnable questionsAndTheirConsequence) {
        withoutAutoSave(() -> {
            questionsAndTheirConsequence.run();
            return null;
        });
    }

    private enum Leaving { CANCELLED, NOTHING_UNSAVED, CHANGES_DISCARDED }

    private Leaving prepareToLeaveDocument() {
        if (isRecording()) {
            if (!prompt.askStopRecording()) {
                return Leaving.CANCELLED;
            }
            stopRecording();
        }
        TimingDocument doc = document;
        if (doc == null || !doc.isDirty()) {
            return Leaving.NOTHING_UNSAVED;
        }
        return switch (prompt.askSaveChanges()) {
            case SAVE -> save() ? Leaving.NOTHING_UNSAVED : Leaving.CANCELLED;
            case DISCARD -> Leaving.CHANGES_DISCARDED;
            case CANCEL -> Leaving.CANCELLED;
        };
    }

    private void replaceDocument(TimingDocument next) {
        TimingDocument old = document;
        RecordingSession oldSession = session;
        if (oldSession != null) {
            oldSession.close(); // frees the server port for the next project
        }
        install(next);
        if (old != null) {
            old.close();
        }
    }

    private void clearWarningsOfTheDocument() {
        writeFailures.reset();
        warnings.clear(StatusWarnings.Kind.CONNECTION_LOST);
        warnings.clear(StatusWarnings.Kind.SERVER);
        warnings.clear(StatusWarnings.Kind.AUTO_SAVE);
    }

    private void install(TimingDocument doc) {
        RecordingSession next = new RecordingSession(doc.database(), readers.get(), time);
        next.addHeatListener(h -> {
            lastHeats = h;
            heatListeners.forEach(l -> l.accept(h));
        });
        next.addStateListener(st -> {
            stateListeners.forEach(l -> l.accept(st));
            fireChange();
        });
        next.addMessageListener(message -> announce(MessageText.describe(message, doc.settings().timekeepingOffsetHours())));
        next.addPingListener(() -> pingListeners.forEach(Runnable::run));
        next.addConnectionLostListener(this::onConnectionLost);
        next.addErrorListener(ex -> announce(MessageText.processingError(ex)));
        next.addWriteListener(new WriteListener() {
            @Override
            public void writeFailed(RecordedData data, Exception cause) {
                writeFailures.failed(data, cause, doc.database().workFile());
                announce(WriteFailures.describe(data, cause, doc.database().workFile()));
                fireChange();
            }

            @Override
            public void writeRecovered(RecordedData data) {
                writeFailures.storedAgain(data);
                fireChange();
            }

            @Override
            public void dataDropped(RecordedData data, int bytes) {
                writeFailures.dropped(data, bytes);
                announce(WriteFailures.describeDropped(data, bytes));
                fireChange();
            }
        });
        doc.addDirtyListener(dirty -> {
            if (!dirty) {
                warnings.clear(StatusWarnings.Kind.AUTO_SAVE);
                writeFailures.saved();
            }
            fireChange();
        });
        clearWarningsOfTheDocument();

        document = doc;
        session = next;
        Settings settings = doc.settings();
        scheduleAutoSave(settings);
        serve(next, settings);
        HeatStore heatStore = new HeatStore(doc.database());
        lastHeats = LiveHeats.of(heatStore.findAll(), heatStore.findUsedLanes());
        heatListeners.forEach(l -> l.accept(lastHeats));
        fireChange();
    }

    private void scheduleAutoSave(Settings settings) {
        int seconds = settings.autoSaveSeconds();
        if (seconds >= 5 && seconds <= 3600) {
            autoSaver.setIntervalSeconds(seconds);
        }
    }

    private static String describe(Exception ex, Path file) {
        return I18n.describe(FileFailure.of(ex, file));
    }

    private void announce(String text) {
        for (Consumer<String> l : messageListeners) {
            try {
                l.accept(text);
            } catch (RuntimeException ex) {
                log.warn("Message listener failed", ex);
            }
        }
    }

    private void fireChange() {
        for (Runnable l : changeListeners) {
            try {
                l.run();
            } catch (RuntimeException ex) {
                log.warn("Change listener failed", ex);
            }
        }
    }
}
