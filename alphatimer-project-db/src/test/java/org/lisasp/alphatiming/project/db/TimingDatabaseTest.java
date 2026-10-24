package org.lisasp.alphatiming.project.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimingDatabaseTest {

    @TempDir
    Path dir;

    private static long countSettings(TimingDatabase db) {
        return db.read(em -> em.createQuery("select count(s) from SettingsEntity s", Long.class).getSingleResult());
    }

    @Test
    void aNewProjectIdentifiesAHeatByEventAndHeatNumberOnly() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            List<Object> heatColumns = db.read(em -> em.createNativeQuery("select name from pragma_table_info('heat')").getResultList());
            List<Object> uniqueKeys = db.read(em -> em.createNativeQuery(
                    "select group_concat(ii.name, ',') from pragma_index_list('heat') il, pragma_index_info(il.name) ii where il.\"unique\" = 1 group by il.name")
                                                 .getResultList());

            assertThat(heatColumns).doesNotContain("competition");
            assertThat(uniqueKeys).containsExactly("event,heatNumber");
            db.closeAndDelete();
        }
    }

    @Test
    void writesAreCountedAndHeardAndSnapshotRoundTrips() throws Exception {
        Path file = dir.resolve("a.heat");
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            List<Long> heard = new ArrayList<>();
            db.addWriteListener(() -> heard.add(db.writeCount()));
            assertThat(db.writeCount()).isZero();
            db.write(em -> {
                em.persist(new SettingsEntity("host", 1234, 60, 1999));
                return null;
            });
            assertThat(heard).containsExactly(1L);
            long saved = db.snapshotTo(file);
            assertThat(saved).isEqualTo(db.writeCount());
            AtomicBoolean unchanged = new AtomicBoolean();
            db.ifUnchangedSince(saved, () -> unchanged.set(true));
            assertThat(unchanged).isTrue();
            db.write(em -> {
                em.persist(new SettingsEntity("host", 1234, 60, 1999));
                return null;
            });
            AtomicBoolean changed = new AtomicBoolean(true);
            db.ifUnchangedSince(saved, () -> changed.set(false));
            assertThat(changed).isTrue();
            db.closeAndDelete();
        }
        try (TimingDatabase db = TimingDatabase.openCopy(file, dir)) {
            assertThat(countSettings(db)).isEqualTo(1);
            assertThat(db.writeCount()).isZero();
            db.closeAndDelete();
        }
    }

    @Test
    void aNewProjectHasNoCheckConstraintsAndTheCurrentSchemaVersion() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir);
             var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + db.workFile());
             var st = connection.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (var rs = st.executeQuery("select sql from sqlite_master where type = 'table' and sql like '%check%'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            assertThat(tables).isEmpty();
            try (var rs = st.executeQuery("pragma user_version")) {
                assertThat(rs.getInt(1)).isEqualTo(SchemaMigrations.CURRENT_VERSION);
            }
        }
    }

    @Test
    void aSavedProjectKeepsItsSchemaVersion() throws Exception {
        Path file = dir.resolve("a.timing");
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            db.snapshotTo(file);
            db.closeAndDelete();
        }
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + file);
             var st = connection.createStatement();
             var rs = st.executeQuery("pragma user_version")) {
            assertThat(rs.getInt(1)).isEqualTo(SchemaMigrations.CURRENT_VERSION);
        }
    }

    @Test
    void openRejectsNonSqliteFileAndCreatesNoWorkFile() throws Exception {
        Path bad = dir.resolve("bad.heat");
        Files.writeString(bad, "this is not a database");
        Path work = Files.createDirectory(dir.resolve("work"));
        assertThatThrownBy(() -> TimingDatabase.openCopy(bad, work)).isInstanceOf(InvalidProjectFileException.class);
        try (var files = Files.list(work)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void concurrentWritersAreSerialized() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            var pool = Executors.newFixedThreadPool(4);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                int n = i;
                futures.add(pool.submit(() -> db.write(em -> {
                    em.persist(new SettingsEntity("h" + n, n + 1, 60, 0));
                    return null;
                })));
            }
            for (Future<?> f : futures) {
                f.get(20, TimeUnit.SECONDS);
            }
            pool.shutdown();
            assertThat(countSettings(db)).isEqualTo(40);
            db.closeAndDelete();
        }
    }
}
