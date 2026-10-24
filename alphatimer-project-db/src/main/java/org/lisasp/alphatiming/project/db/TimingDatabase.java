package org.lisasp.alphatiming.project.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/** All access is serialized by one lock because SQLite allows a single writer and there is a single connection. */
public final class TimingDatabase implements AutoCloseable {

    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

    private static final Class<?>[] ENTITIES = {SettingsEntity.class, RawDataChunkEntity.class, AresMessageEntity.class, LaneTimeEntity.class, HeatEntity.class, LaneEntity.class};

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong writeCount = new AtomicLong();
    private final List<Runnable> writeListeners = new CopyOnWriteArrayList<>();

    private final Path workFile;
    private final SessionFactory factory;

    private LockFile workLock;

    private TimingDatabase(Path workFile, boolean isNew) throws IOException {
        this.workFile = workFile;
        workLock = TimingWorkFolder.lock(workFile);
        try {
            if (isNew) {
                SchemaMigrations.initialize(workFile);
            } else {
                SchemaMigrations.migrate(workFile);
            }
            SessionFactory built = buildFactory(workFile);
            if (isNew) {
                createKeys(built);
            }
            this.factory = built;
        } catch (IOException | RuntimeException ex) {
            releaseLock();
            throw ex;
        }
    }

    private void releaseLock() {
        if (workLock != null) {
            workLock.close();
            workLock = null;
        }
    }

    public static TimingDatabase createNew(Path workDir) throws IOException {
        return new TimingDatabase(TimingWorkFolder.newWorkFile(workDir), true);
    }

    public static TimingDatabase openCopy(Path projectFile, Path workDir) throws IOException {
        checkIsSqlite(projectFile);
        Path work = TimingWorkFolder.newWorkFile(workDir);
        try {
            Files.copy(projectFile, work, StandardCopyOption.REPLACE_EXISTING);
            checkIsTimingDatabase(work, projectFile);
            return new TimingDatabase(work, false);
        } catch (IOException | RuntimeException ex) {
            TimingWorkFolder.discard(work);
            throw ex instanceof IOException io ? io : new InvalidProjectFileException(projectFile, ex);
        }
    }

    public static TimingDatabase adopt(Path workFile) throws IOException {
        return new TimingDatabase(workFile, false);
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
    private static void checkIsTimingDatabase(Path file, Path shownName) throws IOException {
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

    private static void createKeys(SessionFactory built) {
        try {
            built.inTransaction(session -> {
                session.createNativeMutationQuery(HeatEntity.UNIQUE_KEY).executeUpdate();
                // TableRebuild needs foreign keys off only for a referenced table; no table references lane.
                session.doWork(AddLaneKeys::addTo);
            });
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
        c.setProperty("hibernate.connection.foreign_keys", "true");
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
                writeListeners.forEach(Runnable::run);
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
     * Writes a consistent copy of the database into the new file snapshot, flushed to disk; only the copy blocks
     * {@link #write}. On failure the snapshot file is removed.
     *
     * @return the {@link #writeCount()} of the copied state
     */
    public long snapshotTo(Path snapshot) throws IOException {
        lock.lock();
        try (Session session = factory.openSession()) {
            session.doWork(connection -> {
                boolean auto = connection.getAutoCommit();
                connection.setAutoCommit(true); // VACUUM cannot run inside a transaction
                try (Statement st = connection.createStatement()) {
                    st.execute("VACUUM INTO '" + snapshot.toAbsolutePath().toString().replace("'", "''") + "'");
                } finally {
                    connection.setAutoCommit(auto);
                }
            });
            // VACUUM INTO does not sync its output; without this a power loss can leave a half-written project file.
            FileSync.force(snapshot);
            return writeCount.get();
        } catch (IOException | RuntimeException ex) {
            FileSync.deleteTempFile(snapshot);
            throw ex;
        } finally {
            lock.unlock();
        }
    }

    /** Runs action under the database lock unless the database was written since the state with the given {@link #writeCount()}. */
    public void ifUnchangedSince(long savedWriteCount, Runnable action) {
        lock.lock();
        try {
            if (writeCount.get() == savedWriteCount) {
                action.run();
            }
        } finally {
            lock.unlock();
        }
    }

    public long writeCount() {
        return writeCount.get();
    }

    /** Called under the database lock after each committed {@link #write}. */
    public void addWriteListener(Runnable listener) {
        writeListeners.add(listener);
    }

    public Path workFile() {
        return workFile;
    }

    /** Waits for a running database access. */
    @Override
    public void close() {
        lock.lock();
        try {
            factory.close();
            releaseLock();
        } finally {
            lock.unlock();
        }
    }

    public void closeAndDelete() {
        close();
        TimingWorkFolder.discard(workFile);
    }
}
