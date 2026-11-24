package org.lisasp.alphatiming.project.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LenexVersionStoreTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 10, 10, 0);

    @TempDir
    Path dir;

    @Test
    void returnsTheVersionsInOrder() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            LenexVersionStore store = new LenexVersionStore(db);
            store.record(T0, xml("A"), true);
            store.record(T0.plusSeconds(1), xml("B"), true);

            assertThat(store.findAll()).containsExactly(new LenexVersion(T0, xml("A")), new LenexVersion(T0.plusSeconds(1), xml("B")));
        }
    }

    @Test
    void aVersionWithoutResultChangesReplacesThePreviousSuchVersion() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            LenexVersionStore store = new LenexVersionStore(db);
            store.record(T0, xml("A"), true);
            store.record(T0.plusSeconds(1), xml("B"), false);
            store.record(T0.plusSeconds(2), xml("C"), false);

            assertThat(store.findAll()).extracting(LenexVersion::arrival).containsExactly(T0, T0.plusSeconds(2));
        }
    }

    @Test
    void aVersionWithResultChangesReplacesAPrecedingVersionWithout() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            LenexVersionStore store = new LenexVersionStore(db);
            store.record(T0, xml("A"), true);
            store.record(T0.plusSeconds(1), xml("B"), false);
            store.record(T0.plusSeconds(2), xml("C"), true);

            assertThat(store.findAll()).extracting(LenexVersion::arrival).containsExactly(T0, T0.plusSeconds(2));
        }
    }

    @Test
    void versionsAreStoredCompressed() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            byte[] large = xml("A".repeat(10_000));
            new LenexVersionStore(db).record(T0, large, true);

            byte[] stored = db.read(em -> em.createQuery("select v.data from LenexVersionEntity v", byte[].class).getSingleResult());
            assertThat(stored.length).isLessThan(large.length / 10);
        }
    }

    @Test
    void versionsSurviveSavingAndOpening() throws Exception {
        Path saved = dir.resolve("saved.timing");
        try (TimingDatabase db = TimingDatabase.createNew(dir.resolve("first"))) {
            new LenexVersionStore(db).record(T0, xml("A"), true);
            db.snapshotTo(saved);
        }
        try (TimingDatabase db = TimingDatabase.openCopy(saved, Files.createDirectories(dir.resolve("second")))) {
            assertThat(new LenexVersionStore(db).findAll()).containsExactly(new LenexVersion(T0, xml("A")));
        }
    }

    private static byte[] xml(String name) {
        return ("<LENEX version=\"3.0\"><MEETS><MEET name=\"" + name + "\" /></MEETS></LENEX>").getBytes(StandardCharsets.UTF_8);
    }
}
