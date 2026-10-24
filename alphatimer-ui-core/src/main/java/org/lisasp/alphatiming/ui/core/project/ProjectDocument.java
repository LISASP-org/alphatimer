package org.lisasp.alphatiming.ui.core.project;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatiming.project.db.InvalidProjectFileException;
import org.lisasp.alphatiming.project.db.ProjectDatabase;
import org.lisasp.alphatiming.project.db.SettingsEntity;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.SerialMode;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** An open AlphaTiming project: the working database, the project file (once saved) and the dirty state. */
@Slf4j
public final class ProjectDocument implements AutoCloseable {

    private final ProjectDatabase database;
    private volatile Path file;

    private ProjectDocument(ProjectDatabase database, Path file) {
        this.database = database;
        this.file = file;
    }

    /** A new, never saved project; it only counts as changed once something other than its initial settings is written. */
    public static ProjectDocument create(Path workDir, Settings initial) throws IOException {
        ProjectDatabase db = ProjectDatabase.createNew(workDir);
        ProjectDocument doc = new ProjectDocument(db, null);
        doc.updateSettings(initial);
        db.markClean();
        return doc;
    }

    public static ProjectDocument open(Path file, Path workDir) throws IOException {
        ProjectDatabase db = ProjectDatabase.openCopy(file, workDir);
        log.info("Opened project {}", file.getFileName());
        ProjectDocument doc = new ProjectDocument(db, file);
        try {
            doc.settings();
        } catch (RuntimeException ex) {
            db.closeAndDelete();
            throw new InvalidProjectFileException(file, ex);
        }
        return doc;
    }

    public static List<Path> findRecoverable(Path workDir) {
        if (!Files.isDirectory(workDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(workDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(ProjectDatabase.WORK_FILE_SUFFIX))
                        .filter(p -> !ProjectDatabase.isInUse(p))
                        .sorted()
                        .toList();
        } catch (IOException ex) {
            log.warn("Could not look for recoverable work files in {}", workDir, ex);
            return List.of();
        }
    }

    /** Re-opens a work file left behind by a crash; the document is dirty and has no project file yet. */
    public static ProjectDocument recover(Path workFile) throws IOException {
        ProjectDocument doc = new ProjectDocument(ProjectDatabase.adopt(workFile), null);
        doc.touch();
        return doc;
    }

    /** Removes a crash leftover that the user chose not to recover. */
    public static void discard(Path workFile) {
        ProjectDatabase.discard(workFile);
    }

    private void touch() {
        updateSettings(settings());
    }

    public ProjectDatabase database() {
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
                                     .map(ProjectDocument::toSettings)
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
        Path target = file;
        if (target == null) {
            throw new IllegalStateException("The project has no file yet; use saveAs.");
        }
        snapshot(target);
    }

    public void saveAs(Path target) throws IOException {
        snapshot(target);
        file = target;
    }

    private void snapshot(Path target) throws IOException {
        long version = database.writeCount();
        database.snapshotTo(target);
        log.info("Saved project {}", target.getFileName());
        if (database.writeCount() == version) {
            database.markClean();
        }
    }

    @Override
    public void close() {
        database.closeAndDelete();
    }
}
