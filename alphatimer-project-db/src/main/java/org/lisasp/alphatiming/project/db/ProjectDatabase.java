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
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
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

/** All access is serialized by one lock because SQLite allows a single writer and there is a single connection. */
@Slf4j
public final class ProjectDatabase implements AutoCloseable {

    public static final String WORK_FILE_SUFFIX = ".work.sqlite";

    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

    private static final Class<?>[] ENTITIES = {SettingsEntity.class, RawDataChunkEntity.class, AresMessageEntity.class, LaneTimeEntity.class, HeatEntity.class, LaneEntity.class};

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicLong writeCount = new AtomicLong();
    private final List<Consumer<Boolean>> dirtyListeners = new CopyOnWriteArrayList<>();

    private final Path workFile;
    private final SessionFactory factory;

    private FileChannel lockChannel;
    private FileLock fileLock;

    private ProjectDatabase(Path workFile) throws IOException {
        this.workFile = workFile;
        acquireLock();
        try {
            this.factory = buildFactory(workFile);
        } catch (RuntimeException ex) {
            releaseLock();
            throw ex;
        }
    }

    private static Path lockFile(Path workFile) {
        return workFile.resolveSibling(workFile.getFileName() + ".lock");
    }

    /** The lock tells other instances that the work file belongs to a running program, so it is not a crash leftover. */
    private void acquireLock() throws IOException {
        lockChannel = FileChannel.open(lockFile(workFile), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            fileLock = lockChannel.tryLock();
        } catch (OverlappingFileLockException ex) {
            fileLock = null;
        }
        if (fileLock == null) {
            lockChannel.close();
            throw new IOException("Work file is in use: " + workFile);
        }
    }

    private void releaseLock() {
        try {
            if (fileLock != null) {
                fileLock.release();
            }
            if (lockChannel != null) {
                lockChannel.close();
            }
        } catch (IOException ex) {
            log.warn("Could not release lock of {}", workFile, ex);
        }
        fileLock = null;
        lockChannel = null;
    }

    /** True when a running program (this or another one) still owns the work file. */
    public static boolean isInUse(Path workFile) {
        Path lock = lockFile(workFile);
        if (!Files.exists(lock)) {
            return false;
        }
        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.WRITE)) {
            FileLock l = channel.tryLock();
            if (l == null) {
                return true;
            }
            l.release();
            return false;
        } catch (OverlappingFileLockException ex) {
            return true;
        } catch (IOException ex) {
            log.warn("Could not probe the lock of {}; treating the work file as in use", workFile, ex);
            return true;
        }
    }

    public static void discard(Path workFile) {
        try {
            Files.deleteIfExists(workFile);
            Files.deleteIfExists(lockFile(workFile));
        } catch (IOException ex) {
            log.warn("Could not delete {}", workFile, ex);
        }
    }

    public static ProjectDatabase createNew(Path workDir) throws IOException {
        Files.createDirectories(workDir);
        return new ProjectDatabase(newWorkFile(workDir));
    }

    public static ProjectDatabase openCopy(Path projectFile, Path workDir) throws IOException {
        checkIsSqlite(projectFile);
        Files.createDirectories(workDir);
        Path work = newWorkFile(workDir);
        try {
            Files.copy(projectFile, work, StandardCopyOption.REPLACE_EXISTING);
            checkIsProjectDatabase(work, projectFile);
            return new ProjectDatabase(work);
        } catch (IOException | RuntimeException ex) {
            discard(work);
            throw ex instanceof IOException io ? io : new InvalidProjectFileException(projectFile, ex);
        }
    }

    public static ProjectDatabase adopt(Path workFile) throws IOException {
        return new ProjectDatabase(workFile);
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
     * Writes a consistent copy of the database to target (via a temp file and an atomic rename). Does not clear the dirty flag.
     */
    public void snapshotTo(Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.deleteIfExists(tmp);
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
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException cleanup) {
                log.warn("Could not delete temporary file {}", tmp, cleanup);
            }
            throw ex instanceof IOException io ? io : new IOException("Could not save project file " + target, ex);
        } finally {
            lock.unlock();
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

    @Override
    public void close() {
        factory.close();
        releaseLock();
    }

    public void closeAndDelete() {
        close();
        discard(workFile);
    }
}
