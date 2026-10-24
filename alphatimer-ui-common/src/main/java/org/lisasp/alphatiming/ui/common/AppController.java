package org.lisasp.alphatiming.ui.common;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.alphatiming.project.db.InvalidProjectFileException;
import org.lisasp.alphatiming.project.db.ProjectFileInUseException;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.project.db.HeatStore;
import org.lisasp.alphatiming.heats.LiveHeats;
import org.lisasp.alphatiming.ui.core.project.AutoSaver;
import org.lisasp.alphatiming.ui.core.project.CrashLeftover;
import org.lisasp.alphatiming.ui.core.project.ProjectDocument;
import org.lisasp.alphatiming.ui.core.recording.RecordedData;
import org.lisasp.alphatiming.ui.core.recording.RecordingSession;
import org.lisasp.alphatiming.ui.core.recording.RecordingState;
import org.lisasp.alphatiming.ui.core.recording.WriteListener;
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

    private volatile ProjectDocument document;
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
        for (CrashLeftover leftover : ProjectDocument.findRecoverable(workDir)) {
            if (document != null) {
                break; // keep the remaining leftovers for the next start
            }
            offerRecovery(leftover);
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
                        install(ProjectDocument.recover(leftover.workFile()));
                    } catch (IOException | RuntimeException ex) {
                        prompt.showError(I18n.get("error.recovery.title"), describe(ex));
                    }
                    return;
                }
                case DISCARD -> {
                    if (prompt.confirmDiscard(leftover)) {
                        ProjectDocument.discard(leftover.workFile());
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
        if (!prepareToLeaveDocument()) {
            return;
        }
        try {
            replaceDocument(ProjectDocument.create(workDir, defaults.load()));
        } catch (IOException ex) {
            prompt.showError(I18n.get("error.new.title"), describe(ex));
        }
    }

    public void open(Path file) {
        if (!prepareToLeaveDocument()) {
            return;
        }
        ProjectDocument current = document;
        try {
            ProjectDocument opened = current != null && current.isSavedAs(file) ? current.reopen(workDir) : ProjectDocument.open(file, workDir);
            defaults.save(opened.settings());
            replaceDocument(opened);
        } catch (ProjectFileInUseException ex) {
            prompt.showError(I18n.get("error.open.title"), I18n.get("error.open.inUse", file));
        } catch (InvalidProjectFileException ex) {
            prompt.showError(I18n.get("error.open.title"), I18n.get("error.open.invalid", file));
        } catch (IOException | RuntimeException ex) {
            prompt.showError(I18n.get("error.open.title"), I18n.get("error.open.message", file, describe(ex)));
        }
    }

    public boolean save() {
        ProjectDocument doc = document;
        if (doc == null) {
            return false;
        }
        if (doc.file().isEmpty()) {
            return saveAs();
        }
        try {
            doc.save();
            defaults.save(doc.settings());
            fireChange();
            return true;
        } catch (IOException ex) {
            prompt.showError(I18n.get("error.save.title"), I18n.get("error.save.message", describe(ex)));
            return false;
        }
    }

    public boolean saveAs() {
        ProjectDocument doc = document;
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
            prompt.showError(I18n.get("error.save.title"), I18n.get("error.saveTo.message", target.get(), describe(ex)));
            return false;
        }
    }

    /** Asks to stop a running recording, then about unsaved data, and closes the document. True when the program may exit. */
    public boolean requestClose() {
        if (!prepareToLeaveDocument()) {
            return false;
        }
        ProjectDocument doc = document;
        document = null;
        session = null;
        if (doc != null) {
            doc.close();
        }
        return true;
    }

    public void startRecording() {
        ProjectDocument doc = document;
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
        ProjectDocument doc = document;
        RecordingSession s = session;
        if (doc == null) {
            return;
        }
        if (s != null && s.state() == RecordingState.RUNNING) {
            throw new IllegalStateException("Settings cannot be changed while recording.");
        }
        doc.updateSettings(settings);
        scheduleAutoSave(settings);
        fireChange();
    }

    /** Applies settings entered in a form unless they are unchanged or a recording runs; true when they were applied. */
    public boolean applySettingsIfChanged(Settings settings) {
        ProjectDocument doc = document;
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

    public ProjectDocument document() {
        return document;
    }

    public RecordingSession session() {
        return session;
    }

    /** The problems the user should see until they are resolved, the most important first. */
    public List<StatusWarnings.Warning> warnings() {
        return warnings.active();
    }

    void onAutoSaveError(Exception ex) {
        String text = I18n.get("warn.autoSaveFailed", describe(ex));
        warnings.raise(StatusWarnings.Kind.AUTO_SAVE, new StatusWarnings.Warning(text, text));
        announce(text);
        fireChange();
    }

    private void onConnectionLost(String port) {
        String details = I18n.get("warn.connectionLost.detail", port);
        warnings.raise(StatusWarnings.Kind.CONNECTION_LOST, new StatusWarnings.Warning(I18n.get("warn.connectionLost"), details));
        announce(details);
        fireChange();
    }

    public LiveHeats liveHeats() {
        return lastHeats;
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

    private boolean prepareToLeaveDocument() {
        if (isRecording()) {
            if (!prompt.askStopRecording()) {
                return false;
            }
            stopRecording();
        }
        ProjectDocument doc = document;
        if (doc == null || !doc.isDirty()) {
            return true;
        }
        return switch (prompt.askSaveChanges()) {
            case SAVE -> save();
            case DISCARD -> true;
            case CANCEL -> false;
        };
    }

    private void replaceDocument(ProjectDocument next) {
        ProjectDocument old = document;
        install(next);
        if (old != null) {
            old.close();
        }
    }

    private void install(ProjectDocument doc) {
        RecordingSession next = new RecordingSession(doc.database(), readers.get(), time);
        next.addHeatListener(h -> {
            lastHeats = h;
            heatListeners.forEach(l -> l.accept(h));
        });
        next.addStateListener(st -> {
            stateListeners.forEach(l -> l.accept(st));
            fireChange();
        });
        next.addMessageListener(message -> announce(MessageText.describe(message)));
        next.addPingListener(() -> pingListeners.forEach(Runnable::run));
        next.addConnectionLostListener(this::onConnectionLost);
        next.addErrorListener(ex -> announce(I18n.get("log.processingError", ex.toString())));
        next.addWriteListener(new WriteListener() {
            @Override
            public void writeFailed(RecordedData data, Exception cause) {
                writeFailures.failed(data, cause);
                announce(WriteFailures.describe(data, cause));
                fireChange();
            }

            @Override
            public void writeRecovered(RecordedData data) {
                writeFailures.storedAgain(data);
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
        writeFailures.reset();
        warnings.clear(StatusWarnings.Kind.CONNECTION_LOST);

        document = doc;
        session = next;
        Settings settings = doc.settings();
        scheduleAutoSave(settings);
        HeatStore heatStore = new HeatStore(doc.database());
        List<HeatDto> recent = heatStore.findRecent(2);
        lastHeats = recent.isEmpty() ? LiveHeats.NONE : new LiveHeats(recent.get(0), recent.size() > 1 ? recent.get(1) : null, heatStore.findUsedLanes());
        heatListeners.forEach(l -> l.accept(lastHeats));
        fireChange();
    }

    private void scheduleAutoSave(Settings settings) {
        int seconds = settings.autoSaveSeconds();
        if (seconds >= 5 && seconds <= 3600) {
            autoSaver.setIntervalSeconds(seconds);
        }
    }

    static String describe(Exception ex) {
        return ex.getMessage() == null ? ex.toString() : ex.getMessage();
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
