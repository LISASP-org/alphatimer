package org.lisasp.alphatiming.project.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * All access is serialized by one lock because SQLite allows a single writer and there is a single connection. Saves are
 * additionally serialized by a save lock, taken before the database lock, because they share the temp and -previous files.
 */
@Slf4j
public final class ProjectDatabase implements AutoCloseable {

    public static final String WORK_FILE_SUFFIX = ".work.sqlite";

    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

    private static final Class<?>[] ENTITIES = {SettingsEntity.class, RawDataChunkEntity.class, AresMessageEntity.class, LaneTimeEntity.class, HeatEntity.class, LaneEntity.class};

    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock saveLock = new ReentrantLock();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicLong writeCount = new AtomicLong();
    private final List<Consumer<Boolean>> dirtyListeners = new CopyOnWriteArrayList<>();

    private final Path workFile;
    private final SessionFactory factory;

    private LockFile workLock;
    private volatile FileMover mover = (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);

    private ProjectDatabase(Path workFile, boolean isNew) throws IOException {
        this.workFile = workFile;
        acquireLock();
        try {
            if (isNew) {
                SchemaMigrations.initialize(workFile);
            } else {
                SchemaMigrations.migrate(workFile);
            }
            SessionFactory built = buildFactory(workFile);
            if (isNew) {
                createUniqueKeys(built);
            }
            this.factory = built;
        } catch (IOException | RuntimeException ex) {
            releaseLock();
            throw ex;
        }
    }

    /** The lock tells other instances that the work file belongs to a running program, so it is not a crash leftover. */
    private void acquireLock() throws IOException {
        workLock = LockFile.tryAcquire(LockFile.besides(workFile)).orElseThrow(() -> new IOException("Work file is in use: " + workFile));
    }

    private void releaseLock() {
        if (workLock != null) {
            workLock.close();
            workLock = null;
        }
    }

    /** True when a running program (this or another one) still owns the work file. */
    public static boolean isInUse(Path workFile) {
        return LockFile.isHeld(LockFile.besides(workFile));
    }

    public static void discard(Path workFile) {
        try {
            Files.deleteIfExists(workFile);
            Files.deleteIfExists(LockFile.besides(workFile));
        } catch (IOException ex) {
            log.warn("Could not delete {}", workFile, ex);
        }
    }

    public static ProjectDatabase createNew(Path workDir) throws IOException {
        Files.createDirectories(workDir);
        return new ProjectDatabase(newWorkFile(workDir), true);
    }

    public static ProjectDatabase openCopy(Path projectFile, Path workDir) throws IOException {
        checkIsSqlite(projectFile);
        Files.createDirectories(workDir);
        Path work = newWorkFile(workDir);
        try {
            Files.copy(projectFile, work, StandardCopyOption.REPLACE_EXISTING);
            checkIsProjectDatabase(work, projectFile);
            return new ProjectDatabase(work, false);
        } catch (IOException | RuntimeException ex) {
            discard(work);
            throw ex instanceof IOException io ? io : new InvalidProjectFileException(projectFile, ex);
        }
    }

    public static ProjectDatabase adopt(Path workFile) throws IOException {
        return new ProjectDatabase(workFile, false);
    }

    private static Path newWorkFile(Path workDir) {
        return workDir.resolve("heat-" + UUID.randomUUID() + WORK_FILE_SUFFIX);
    }

    private static void checkIsSqlite(Path file) throws IOException {
        byte[] header = new byte[SQLITE_HEADER.length];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.readNBytes(header, 0, header.length);
            if (read != header.length || !Arrays.equals(header, SQLITE_HEADER)) {
                throw new InvalidProjectFileException(file);
            }
        }
    }

    /** Hibernate would silently add its tables to any SQLite file, so foreign files are rejected before it sees them. */
    private static void checkIsProjectDatabase(Path file, Path shownName) throws IOException {
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             Statement st = c.createStatement();
             java.sql.ResultSet rs = st.executeQuery("select count(*) from sqlite_master where type = 'table' and name in ('settings', 'heat', 'rawdata', 'aresmessage')")) {
            if (!rs.next() || rs.getInt(1) < 4) {
                throw new InvalidProjectFileException(shownName);
            }
        } catch (java.sql.SQLException ex) {
            throw new InvalidProjectFileException(shownName, ex);
        }
    }

    private static void createUniqueKeys(SessionFactory built) {
        try {
            built.inTransaction(session -> session.createNativeMutationQuery(HeatEntity.UNIQUE_KEY).executeUpdate());
        } catch (RuntimeException ex) {
            built.close();
            throw ex;
        }
    }

    private static SessionFactory buildFactory(Path workFile) {
        Configuration c = new Configuration();
        c.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC");
        c.setProperty("hibernate.connection.url", "jdbc:sqlite:" + workFile.toAbsolutePath());
        c.setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        c.setProperty("hibernate.hbm2ddl.auto", "update");
        c.setProperty("hibernate.connection.pool_size", "1");
        c.setProperty("hibernate.show_sql", "false");
        for (Class<?> e : ENTITIES) {
            c.addAnnotatedClass(e);
        }
        return c.buildSessionFactory();
    }

    public <T> T read(Function<EntityManager, T> work) {
        lock.lock();
        try (EntityManager em = factory.createEntityManager()) {
            return work.apply(em);
        } finally {
            lock.unlock();
        }
    }

    public <T> T write(Function<EntityManager, T> work) {
        lock.lock();
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                T result = work.apply(em);
                tx.commit();
                writeCount.incrementAndGet();
                setDirty(true);
                return result;
            } catch (RuntimeException ex) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw ex;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Writes a consistent copy of the database to target (via temp files and an atomic rename); an existing target becomes
     * {@link #previousVersionOf(Path)}. Concurrent calls run one after another. Only the copy of the database blocks
     * {@link #write}; moving it to the target does not. Does not clear the dirty flag.
     *
     * @return the {@link #writeCount()} of the saved state
     */
    public long snapshotTo(Path target) throws IOException {
        saveLock.lock();
        try {
            return snapshotUnderSaveLock(target);
        } finally {
            saveLock.unlock();
        }
    }

    @FunctionalInterface
    public interface SaveWork<E extends Exception> {
        void run() throws E;
    }

    /** Runs work while no save of this database runs; work may save itself, the save lock is reentrant. */
    public <E extends Exception> void runWithSaveLock(SaveWork<E> work) throws E {
        saveLock.lock();
        try {
            work.run();
        } finally {
            saveLock.unlock();
        }
    }

    /** Marks the database clean unless it was written since the state with the given {@link #writeCount()}. */
    public void markCleanIfUnchangedSince(long savedWriteCount) {
        lock.lock();
        try {
            if (writeCount.get() == savedWriteCount) {
                setDirty(false);
            }
        } finally {
            lock.unlock();
        }
    }

    private long snapshotUnderSaveLock(Path target) throws IOException {
        Path localTmp = workFile.resolveSibling(workFile.getFileName() + "." + UUID.randomUUID() + ".tmp");
        Path targetTmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            long savedWriteCount = copyTo(localTmp);
            Files.deleteIfExists(targetTmp);
            moveToTargetFolder(localTmp, targetTmp);
            if (Files.exists(target)) {
                Path previous = previousVersionOf(target);
                Files.deleteIfExists(previous);
                Files.move(target, previous, StandardCopyOption.ATOMIC_MOVE);
            }
            Files.move(targetTmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            forceDirectory(target.toAbsolutePath().getParent());
            return savedWriteCount;
        } catch (IOException | RuntimeException ex) {
            deleteTempFile(localTmp);
            deleteTempFile(targetTmp);
            throw ex instanceof IOException io ? io : new IOException("Could not save project file " + target, ex);
        }
    }

    private long copyTo(Path tmp) throws IOException {
        lock.lock();
        try (Session session = factory.openSession()) {
            session.doWork(connection -> {
                boolean auto = connection.getAutoCommit();
                connection.setAutoCommit(true); // VACUUM cannot run inside a transaction
                try (Statement st = connection.createStatement()) {
                    st.execute("VACUUM INTO '" + tmp.toAbsolutePath().toString().replace("'", "''") + "'");
                } finally {
                    connection.setAutoCommit(auto);
                }
            });
            // VACUUM INTO does not sync its output; without this a power loss can leave a half-written project file.
            force(tmp);
            return writeCount.get();
        } finally {
            lock.unlock();
        }
    }

    private void moveToTargetFolder(Path localTmp, Path targetTmp) throws IOException {
        try {
            mover.moveAtomically(localTmp, targetTmp);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.copy(localTmp, targetTmp);
            force(targetTmp);
            deleteTempFile(localTmp);
        }
    }

    private static void deleteTempFile(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ex) {
            log.warn("Could not delete temporary file {}", tmp, ex);
        }
    }

    @FunctionalInterface
    interface FileMover {
        void moveAtomically(Path source, Path target) throws IOException;
    }

    void useMover(FileMover mover) {
        this.mover = mover;
    }

    /** The file that keeps the state before the last save: {@code wk.timing} → {@code wk-previous.timing}. */
    public static Path previousVersionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String previous = dot > 0 ? name.substring(0, dot) + "-previous" + name.substring(dot) : name + "-previous";
        return file.resolveSibling(previous);
    }

    private static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ex) {
            // Windows cannot open a directory as a channel; there the renames are not synced separately.
            log.debug("Could not sync directory {}", directory, ex);
        }
    }

    public long writeCount() {
        return writeCount.get();
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void markClean() {
        setDirty(false);
    }

    private void setDirty(boolean value) {
        if (dirty.getAndSet(value) != value) {
            dirtyListeners.forEach(l -> l.accept(value));
        }
    }

    public void addDirtyListener(Consumer<Boolean> listener) {
        dirtyListeners.add(listener);
    }

    public Path workFile() {
        return workFile;
    }

    /** Waits for a running save and database access. */
    @Override
    public void close() {
        saveLock.lock();
        lock.lock();
        try {
            factory.close();
            releaseLock();
        } finally {
            lock.unlock();
            saveLock.unlock();
        }
    }

    public void closeAndDelete() {
        close();
        discard(workFile);
    }
}
