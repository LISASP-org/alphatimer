package org.lisasp.alphatiming.ui.core.project;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatiming.project.db.InvalidProjectFileException;
import org.lisasp.alphatiming.project.db.LockFile;
import org.lisasp.alphatiming.project.db.ProjectFileInUseException;
import org.lisasp.alphatiming.project.db.SettingsEntity;
import org.lisasp.alphatiming.project.db.TimingDatabase;
import org.lisasp.alphatiming.project.db.TimingFileWriter;
import org.lisasp.alphatiming.project.db.TimingWorkFolder;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.SerialMode;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Saves (Save, Save As, auto-save) run one after another on the save lock, taken before the database lock; switching to
 * another file, the origin note and closing run under it too, so a running save finishes before the database closes.
 */
@Slf4j
public final class TimingDocument implements AutoCloseable {

    private final TimingDatabase database;
    private final ReentrantLock saveLock = new ReentrantLock();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final List<Consumer<Boolean>> dirtyListeners = new CopyOnWriteArrayList<>();
    private volatile Path file;
    private volatile LockFile fileLock;
    private boolean closed;
    private volatile TimingFileWriter fileWriter = new TimingFileWriter();

    private TimingDocument(TimingDatabase database, Path file, LockFile fileLock) {
        this.database = database;
        this.file = file;
        this.fileLock = fileLock;
        database.addWriteListener(() -> setDirty(true));
    }

    /** A new, never saved project; it only counts as changed once something other than its initial settings is written. */
    public static TimingDocument create(Path workDir, Settings initial) throws IOException {
        TimingDatabase db = TimingDatabase.createNew(workDir);
        TimingDocument doc = new TimingDocument(db, null, null);
        doc.updateSettings(initial);
        doc.setDirty(false);
        return doc;
    }

    public static TimingDocument open(Path file, Path workDir) throws IOException {
        LockFile lock = lockProjectFile(file);
        try {
            return load(file, workDir, lock);
        } catch (IOException | RuntimeException ex) {
            lock.closeAndDelete();
            throw ex;
        }
    }

    /** Opens the saved state of this document's file once more; the new document takes over the lock on the file. */
    public TimingDocument reopen(Path workDir) throws IOException {
        saveLock.lock();
        try {
            checkOpen();
            Path current = file;
            if (current == null) {
                throw new IllegalStateException("The project has no file yet.");
            }
            TimingDocument reopened = load(current, workDir, fileLock);
            fileLock = null;
            return reopened;
        } finally {
            saveLock.unlock();
        }
    }

    public boolean isSavedAs(Path candidate) {
        Path current = file;
        return current != null && current.toAbsolutePath().normalize().equals(candidate.toAbsolutePath().normalize());
    }

    private static LockFile lockProjectFile(Path file) throws IOException {
        return LockFile.tryAcquire(LockFile.besides(file)).orElseThrow(() -> new ProjectFileInUseException(file));
    }

    private static TimingDocument load(Path file, Path workDir, LockFile lock) throws IOException {
        TimingDatabase db = TimingDatabase.openCopy(file, workDir);
        log.info("Opened project {}", file.getFileName());
        TimingDocument doc = new TimingDocument(db, file, lock);
        try {
            doc.settings();
        } catch (RuntimeException ex) {
            db.closeAndDelete();
            throw new InvalidProjectFileException(file, ex);
        }
        TimingWorkFolder.recordOrigin(db.workFile(), file);
        return doc;
    }

    /**
     * Re-opens a work file left behind by a crash as a dirty document. It keeps its project file only while that file
     * is free and unchanged since the crashed session last opened or saved it; otherwise Save would overwrite newer data.
     * If recovering fails, the work file stays a leftover that can be recovered or discarded later.
     */
    public static TimingDocument recover(Path workFile) throws IOException {
        Optional<TimingWorkFolder.Origin> origin = TimingWorkFolder.origin(workFile).filter(TimingWorkFolder.Origin::isUnchanged);
        Optional<LockFile> lock = origin.isPresent() ? LockFile.tryAcquire(LockFile.besides(origin.get().projectFile())) : Optional.empty();
        Path file = lock.isPresent() ? origin.get().projectFile() : null;
        TimingDatabase db = null;
        try {
            db = TimingDatabase.adopt(workFile);
            TimingDocument doc = new TimingDocument(db, file, lock.orElse(null));
            doc.touch();
            return doc;
        } catch (IOException | RuntimeException ex) {
            if (db != null) {
                db.close();
            }
            lock.ifPresent(LockFile::closeAndDelete);
            throw ex;
        }
    }

    private void touch() {
        updateSettings(settings());
    }

    public TimingDatabase database() {
        return database;
    }

    public Optional<Path> file() {
        return Optional.ofNullable(file);
    }

    public Settings settings() {
        return database.read(em -> em.createQuery("select s from SettingsEntity s order by s.id", SettingsEntity.class)
                                     .setMaxResults(1)
                                     .getResultStream()
                                     .findFirst()
                                     .map(TimingDocument::toSettings)
                                     .orElseGet(Settings::defaults));
    }

    public void updateSettings(Settings s) {
        database.write(em -> {
            SettingsEntity e = em.createQuery("select s from SettingsEntity s order by s.id", SettingsEntity.class)
                                 .setMaxResults(1)
                                 .getResultStream()
                                 .findFirst()
                                 .orElseGet(() -> {
                                     SettingsEntity created = new SettingsEntity();
                                     em.persist(created);
                                     return created;
                                 });
            e.setTcpHost(s.tcpHost());
            e.setTcpPort(s.tcpPort());
            e.setAutoSaveSeconds(s.autoSaveSeconds());
            e.setRestPort(s.restPort());
            e.setConnectionType(s.connectionType().name());
            e.setSerialPort(s.serialPort());
            e.setSerialMode(s.serialMode().name());
            e.setRestEnabled(s.restEnabled());
            e.setReplayFile(s.replayFile());
            e.setCompetitionKey(s.competitionKey());
            return null;
        });
    }

    private static Settings toSettings(SettingsEntity s) {
        return new Settings(parse(ConnectionType.class, s.getConnectionType()), s.getTcpHost(), s.getTcpPort(), s.getSerialPort(), parse(SerialMode.class, s.getSerialMode()), s.getAutoSaveSeconds(),
                            s.getRestPort() == 0 ? Settings.DEFAULT_REST_PORT : s.getRestPort(),
                            s.getRestEnabled() == null ? s.getRestPort() != 0 : s.getRestEnabled(),
                            s.getReplayFile(),
                            s.getCompetitionKey());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name) {
        try {
            return name == null ? null : Enum.valueOf(type, name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void addDirtyListener(Consumer<Boolean> listener) {
        dirtyListeners.add(listener);
    }

    private void setDirty(boolean value) {
        if (dirty.getAndSet(value) != value) {
            dirtyListeners.forEach(l -> l.accept(value));
        }
    }

    public void save() throws IOException {
        saveLock.lock();
        try {
            checkOpen();
            Path target = file;
            if (target == null) {
                throw new IllegalStateException("The project has no file yet; use saveAs.");
            }
            snapshot(target);
        } finally {
            saveLock.unlock();
        }
    }

    /** Saves unsaved changes to the project file; does nothing without a file or once the document is closed. */
    public void saveIfChanged() throws IOException {
        saveLock.lock();
        try {
            Path target = file;
            if (!closed && target != null && isDirty()) {
                snapshot(target);
            }
        } finally {
            saveLock.unlock();
        }
    }

    public void saveAs(Path target) throws IOException {
        saveLock.lock();
        try {
            checkOpen();
            if (isSavedAs(target)) {
                snapshot(target);
                return;
            }
            LockFile targetLock = lockProjectFile(target);
            try {
                snapshot(target);
            } catch (IOException | RuntimeException ex) {
                targetLock.closeAndDelete();
                throw ex;
            }
            LockFile previous = fileLock;
            fileLock = targetLock;
            file = target;
            if (previous != null) {
                previous.closeAndDelete();
            }
        } finally {
            saveLock.unlock();
        }
    }

    void useFileWriter(TimingFileWriter writer) {
        this.fileWriter = writer;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("The project is closed.");
        }
    }

    private void snapshot(Path target) throws IOException {
        Path snapshot = TimingWorkFolder.newSnapshotFile(database.workFile());
        long savedWriteCount;
        try {
            savedWriteCount = database.snapshotTo(snapshot);
            fileWriter.write(snapshot, target);
        } catch (RuntimeException ex) {
            throw new IOException("Could not save project file " + target, ex);
        }
        log.info("Saved project {}", target.getFileName());
        TimingWorkFolder.recordOrigin(database.workFile(), target);
        database.ifUnchangedSince(savedWriteCount, () -> setDirty(false));
    }

    /** Waits for a running save. */
    @Override
    public void close() {
        saveLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            database.closeAndDelete();
            LockFile lock = fileLock;
            fileLock = null;
            if (lock != null) {
                lock.closeAndDelete();
            }
        } finally {
            saveLock.unlock();
        }
    }
}
