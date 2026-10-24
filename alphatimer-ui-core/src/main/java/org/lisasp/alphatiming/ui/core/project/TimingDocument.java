package org.lisasp.alphatiming.ui.core.project;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatiming.project.db.InvalidProjectFileException;
import org.lisasp.alphatiming.project.db.LockFile;
import org.lisasp.alphatiming.project.db.TimingDatabase;
import org.lisasp.alphatiming.project.db.ProjectFileInUseException;
import org.lisasp.alphatiming.project.db.SettingsEntity;
import org.lisasp.alphatiming.project.db.TimingFileWriter;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.SerialMode;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
public final class TimingDocument implements AutoCloseable {

    private final TimingDatabase database;
    private volatile Path file;
    private volatile LockFile fileLock;
    private boolean closed;
    private volatile TimingFileWriter fileWriter = new TimingFileWriter();

    private TimingDocument(TimingDatabase database, Path file, LockFile fileLock) {
        this.database = database;
        this.file = file;
        this.fileLock = fileLock;
    }

    /** A new, never saved project; it only counts as changed once something other than its initial settings is written. */
    public static TimingDocument create(Path workDir, Settings initial) throws IOException {
        TimingDatabase db = TimingDatabase.createNew(workDir);
        TimingDocument doc = new TimingDocument(db, null, null);
        doc.updateSettings(initial);
        db.markClean();
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
        List<TimingDocument> reopened = new ArrayList<>(1);
        database.runWithSaveLock(() -> {
            checkOpen();
            Path current = file;
            if (current == null) {
                throw new IllegalStateException("The project has no file yet.");
            }
            reopened.add(load(current, workDir, fileLock));
            fileLock = null;
        });
        return reopened.getFirst();
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
        WorkFileOrigin.record(db.workFile(), file);
        return doc;
    }

    public static List<CrashLeftover> findRecoverable(Path workDir) {
        if (!Files.isDirectory(workDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(workDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(TimingDatabase.WORK_FILE_SUFFIX))
                        .filter(p -> !TimingDatabase.isInUse(p))
                        .sorted()
                        .map(TimingDocument::describeLeftover)
                        .flatMap(Optional::stream)
                        .toList();
        } catch (IOException ex) {
            log.warn("Could not look for recoverable work files in {}", workDir, ex);
            return List.of();
        }
    }

    private static Optional<CrashLeftover> describeLeftover(Path workFile) {
        try {
            return Optional.of(new CrashLeftover(workFile, Files.getLastModifiedTime(workFile).toInstant(),
                                                 WorkFileOrigin.read(workFile).map(WorkFileOrigin::projectFile)));
        } catch (IOException ex) {
            log.warn("Could not read the work file {}", workFile.getFileName(), ex);
            return Optional.empty();
        }
    }

    /**
     * Re-opens a work file left behind by a crash as a dirty document. It keeps its project file only while that file
     * is free and unchanged since the crashed session last opened or saved it; otherwise Save would overwrite newer data.
     * If recovering fails, the work file stays a leftover that can be recovered or discarded later.
     */
    public static TimingDocument recover(Path workFile) throws IOException {
        Optional<WorkFileOrigin> origin = WorkFileOrigin.read(workFile).filter(WorkFileOrigin::isUnchanged);
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

    public static void discard(Path workFile) {
        TimingDatabase.discard(workFile);
        WorkFileOrigin.delete(workFile);
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
        return database.isDirty();
    }

    public void addDirtyListener(Consumer<Boolean> listener) {
        database.addDirtyListener(listener);
    }

    public void save() throws IOException {
        database.runWithSaveLock(() -> {
            checkOpen();
            Path target = file;
            if (target == null) {
                throw new IllegalStateException("The project has no file yet; use saveAs.");
            }
            snapshot(target);
        });
    }

    /** Saves unsaved changes to the project file; does nothing without a file or once the document is closed. */
    public void saveIfChanged() throws IOException {
        database.runWithSaveLock(() -> {
            Path target = file;
            if (!closed && target != null && isDirty()) {
                snapshot(target);
            }
        });
    }

    public void saveAs(Path target) throws IOException {
        database.runWithSaveLock(() -> {
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
        });
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
        Path workFile = database.workFile();
        Path snapshot = workFile.resolveSibling(workFile.getFileName() + "." + UUID.randomUUID() + ".tmp");
        long savedWriteCount;
        try {
            savedWriteCount = database.snapshotTo(snapshot);
            fileWriter.write(snapshot, target);
        } catch (RuntimeException ex) {
            throw new IOException("Could not save project file " + target, ex);
        }
        log.info("Saved project {}", target.getFileName());
        WorkFileOrigin.record(database.workFile(), target);
        database.markCleanIfUnchangedSince(savedWriteCount);
    }

    /** Waits for a running save. */
    @Override
    public void close() {
        database.runWithSaveLock(() -> {
            if (closed) {
                return;
            }
            closed = true;
            database.closeAndDelete();
            WorkFileOrigin.delete(database.workFile());
            LockFile lock = fileLock;
            fileLock = null;
            if (lock != null) {
                lock.closeAndDelete();
            }
        });
    }
}
