package org.lisasp.alphatiming.project.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
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
    void aNewProjectHasNoDerivedTables() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            List<Object> tables = db.read(em -> em.createNativeQuery("select name from sqlite_master where type = 'table'").getResultList());

            assertThat(tables).contains("settings", "rawdata", "aresmessage").doesNotContain("heat", "lane", "lanetime");
            Object version = db.read(em -> em.createNativeQuery("pragma user_version").getSingleResult());
            assertThat(version).isEqualTo(1);
            db.closeAndDelete();
        }
    }

    @Test
    void aWorkFileBeingCopiedIsNotOfferedAsACrashLeftover() throws Exception {
        Path file = dir.resolve("a.timing");
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            db.snapshotTo(file);
            db.closeAndDelete();
        }
        Path work = dir.resolve("work");
        List<CrashLeftover> seenWhileCopying = new ArrayList<>();
        TimingDatabase.FileCopy copyAndLookForLeftovers = (from, to) -> {
            byte[] content = Files.readAllBytes(from);
            Files.write(to, Arrays.copyOf(content, content.length / 2));
            seenWhileCopying.addAll(TimingWorkFolder.findLeftovers(work));
            Files.write(to, content);
        };

        try (TimingDatabase db = TimingDatabase.openCopy(file, work, copyAndLookForLeftovers)) {
            assertThat(seenWhileCopying).isEmpty();
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

    private static final LocalDateTime TEN_O_CLOCK = LocalDateTime.of(2026, 5, 1, 10, 0);

    private Path projectFile(String timeZone) throws Exception {
        Path file = dir.resolve("zone.timing");
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            db.write(em -> {
                SettingsEntity settings = new SettingsEntity("host", 1234, 60, 1999);
                settings.setTimeZone(timeZone);
                em.persist(settings);
                return null;
            });
            db.snapshotTo(file);
            db.closeAndDelete();
        }
        return file;
    }

    private static long storedMillis(TimingDatabase db, String table) {
        return db.read(em -> ((Number) em.createNativeQuery("select cast(timestamp as integer) from " + table).getSingleResult()).longValue());
    }

    @Test
    void timesAreStoredAsEpochMillisInTheZoneOfTheProject() throws Exception {
        long tenInNewYork = TEN_O_CLOCK.atZone(ZoneId.of("America/New_York")).toInstant().toEpochMilli();
        try (TimingDatabase db = TimingDatabase.openCopy(projectFile("America/New_York"), Files.createDirectory(dir.resolve("work")))) {
            new MessageStore(db).put(MessageStoreTest.message("TestWK", (short) 1, (byte) 1, TEN_O_CLOCK));
            new LenexVersionStore(db).record(TEN_O_CLOCK, "<LENEX />".getBytes(StandardCharsets.UTF_8), true);
            db.write(em -> {
                em.persist(new RawDataChunkEntity(TEN_O_CLOCK, new byte[] {1}));
                return null;
            });

            assertThat(storedMillis(db, "aresmessage")).isEqualTo(tenInNewYork);
            assertThat(storedMillis(db, "lenexversion")).isEqualTo(tenInNewYork);
            assertThat(storedMillis(db, "rawdata")).isEqualTo(tenInNewYork);
            db.closeAndDelete();
        }
    }

    @Test
    void storedTimesAreReadInTheZoneOfTheProject() throws Exception {
        long tenInNewYork = TEN_O_CLOCK.atZone(ZoneId.of("America/New_York")).toInstant().toEpochMilli();
        try (TimingDatabase db = TimingDatabase.openCopy(projectFile("America/New_York"), Files.createDirectory(dir.resolve("work")))) {
            LenexVersionStore store = new LenexVersionStore(db);
            store.record(TEN_O_CLOCK.minusHours(3), "<LENEX />".getBytes(StandardCharsets.UTF_8), true);
            db.write(em -> em.createNativeQuery("update lenexversion set timestamp = " + tenInNewYork).executeUpdate());

            assertThat(store.findAll().getFirst().arrival()).isEqualTo(TEN_O_CLOCK);
            db.closeAndDelete();
        }
    }

    @Test
    void aFileWithoutTimeZoneIsReadInEuropeBerlinAndKeepsThatZone() throws Exception {
        long tenInBerlin = TEN_O_CLOCK.atZone(ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli();
        try (TimingDatabase db = TimingDatabase.openCopy(projectFile(null), Files.createDirectory(dir.resolve("work")))) {
            LenexVersionStore store = new LenexVersionStore(db);
            store.record(TEN_O_CLOCK.minusHours(3), "<LENEX />".getBytes(StandardCharsets.UTF_8), true);
            db.write(em -> em.createNativeQuery("update lenexversion set timestamp = " + tenInBerlin).executeUpdate());

            assertThat(store.findAll().getFirst().arrival()).isEqualTo(TEN_O_CLOCK);
            assertThat(db.timeZone()).isEqualTo(ZoneId.of("Europe/Berlin"));
            Object stored = db.read(em -> em.createNativeQuery("select timeZone from settings").getSingleResult());
            assertThat(stored).isEqualTo("Europe/Berlin");
            db.closeAndDelete();
        }
    }

    @Test
    void aNewProjectTakesTheZoneOfThisComputer() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            assertThat(db.timeZone()).isEqualTo(ZoneId.systemDefault());
            db.closeAndDelete();
        }
    }
}
