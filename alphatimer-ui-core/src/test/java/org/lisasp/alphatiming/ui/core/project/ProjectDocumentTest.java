package org.lisasp.alphatiming.ui.core.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatiming.project.db.InvalidProjectFileException;
import org.lisasp.alphatiming.project.db.ProjectDatabase;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.project.db.HeatStore;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.project.db.MessageStore;
import org.lisasp.alphatiming.project.db.RawDataRecorder;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.SerialMode;
import org.lisasp.alphatiming.ui.core.settings.Settings;
import org.lisasp.alphatiming.project.db.testdata.DataHandlingMessageTestData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectDocumentTest {

    private static final Settings SETTINGS = new Settings("host", 8585, 60, 1999).withCompetitionKey("WK");

    @TempDir
    Path dir;

    private Path work() throws IOException {
        return Files.createDirectories(dir.resolve("work"));
    }

    private long workFiles() throws IOException {
        try (var files = Files.list(work())) {
            return files.count();
        }
    }

    @Test
    void newProjectIsCleanUntilSomethingChangesAndStaysFileless() throws Exception {
        try (ProjectDocument doc = ProjectDocument.create(work(), SETTINGS)) {
            assertThat(doc.isDirty()).isFalse();
            assertThat(doc.file()).isEmpty();
            doc.updateSettings(SETTINGS.withTcp("changed", 1));
            assertThat(doc.isDirty()).isTrue();
            doc.saveAs(dir.resolve("x.heat"));
            assertThat(doc.isDirty()).isFalse();
            assertThat(doc.file()).contains(dir.resolve("x.heat"));
            doc.updateSettings(SETTINGS.withTcp("other", 1));
            assertThat(doc.isDirty()).isTrue();
            doc.save();
            assertThat(doc.isDirty()).isFalse();
        }
    }

    @Test
    void openRestoresSettingsAndCloseRemovesWorkFile() throws Exception {
        Path file = dir.resolve("x.heat");
        try (ProjectDocument doc = ProjectDocument.create(work(), SETTINGS)) {
            doc.saveAs(file);
        }
        assertThat(workFiles()).isZero();
        try (ProjectDocument doc = ProjectDocument.open(file, work())) {
            assertThat(doc.settings()).isEqualTo(SETTINGS);
            assertThat(doc.isDirty()).isFalse();
            assertThat(doc.file()).contains(file);
        }
        assertThat(workFiles()).isZero();
    }

    @Test
    void saveWithoutFileIsRejected() throws Exception {
        try (ProjectDocument doc = ProjectDocument.create(work(), SETTINGS)) {
            assertThatThrownBy(doc::save).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void failedSaveKeepsDocumentDirtyAndLeavesNoTempFile() throws Exception {
        try (ProjectDocument doc = ProjectDocument.create(work(), SETTINGS)) {
            doc.updateSettings(SETTINGS.withTcp("changed", 1));
            Path target = dir.resolve("missing/x.heat");
            assertThatThrownBy(() -> doc.saveAs(target)).isInstanceOf(IOException.class);
            assertThat(doc.isDirty()).isTrue();
            assertThat(doc.file()).isEmpty();
            assertThat(dir.resolve("missing")).doesNotExist();
        }
    }

    @Test
    void writeAfterSaveKeepsDocumentDirty() throws Exception {
        try (ProjectDocument doc = ProjectDocument.create(work(), SETTINGS)) {
            doc.saveAs(dir.resolve("x.heat"));
            assertThat(doc.isDirty()).isFalse();
            long before = doc.database().writeCount();
            doc.updateSettings(SETTINGS.withTcp("b", 2));
            assertThat(doc.database().writeCount()).isGreaterThan(before);
            assertThat(doc.isDirty()).isTrue();
        }
    }

    @Test
    void openRejectsForeignFileWithoutLeavingWorkFiles() throws Exception {
        Path bad = dir.resolve("bad.heat");
        Files.writeString(bad, "nope");
        assertThatThrownBy(() -> ProjectDocument.open(bad, work())).isInstanceOfSatisfying(InvalidProjectFileException.class, ex -> assertThat(ex.file()).isEqualTo(bad));
        assertThat(workFiles()).isZero();
    }

    @Test
    void openRejectsForeignSqliteFileAndDoesNotTouchIt() throws Exception {
        Path other = dir.resolve("other.sqlite");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + other); var st = connection.createStatement()) {
            st.execute("create table results (id integer primary key, name text)");
        }
        long size = Files.size(other);
        assertThatThrownBy(() -> ProjectDocument.open(other, work())).isInstanceOf(InvalidProjectFileException.class);
        assertThat(workFiles()).isZero();
        assertThat(Files.size(other)).isEqualTo(size);
    }

    @Test
    void crashLeftoversCanBeFoundAndRecovered() throws Exception {
        ProjectDocument doc = ProjectDocument.create(work(), SETTINGS);
        doc.database().close(); // simulate a crash: the work file stays
        var found = ProjectDocument.findRecoverable(work());
        assertThat(found).hasSize(1);
        try (ProjectDocument recovered = ProjectDocument.recover(found.get(0))) {
            assertThat(recovered.settings()).isEqualTo(SETTINGS);
            assertThat(recovered.isDirty()).isTrue();
            assertThat(recovered.file()).isEmpty();
        }
        assertThat(ProjectDocument.findRecoverable(work())).isEmpty();
    }

    @Test
    void workFilesOfOpenDocumentsAreNotOfferedForRecovery() throws Exception {
        try (ProjectDocument live = ProjectDocument.create(work(), SETTINGS)) {
            assertThat(ProjectDocument.findRecoverable(work())).isEmpty();
            live.database().close();
            assertThat(ProjectDocument.findRecoverable(work())).hasSize(1);
        }
    }

    @Test
    void discardRemovesWorkFileAndItsLock() throws Exception {
        ProjectDocument doc = ProjectDocument.create(work(), SETTINGS);
        doc.database().close();
        ProjectDocument.discard(ProjectDocument.findRecoverable(work()).get(0));
        assertThat(workFiles()).isZero();
    }

    @Test
    void serialSettingsSurviveSaveAndOpen() throws Exception {
        Settings serial = new Settings(ConnectionType.SERIAL, "host", 8585, "COM3", SerialMode.QUANTUM, 60, 1999, true).withCompetitionKey("WK");
        Path file = dir.resolve("serial.heat");
        try (ProjectDocument doc = ProjectDocument.create(work(), serial)) {
            doc.saveAs(file);
        }
        try (ProjectDocument doc = ProjectDocument.open(file, work())) {
            assertThat(doc.settings()).isEqualTo(serial);
        }
    }

    @Test
    void replayFileSurvivesSaveAndOpen() throws Exception {
        Settings s = new Settings("host", 8585, 60, 1999).withCompetitionKey("WK").withConnectionType(ConnectionType.FILE).withReplayFile("/data/run.serial");
        Path file = dir.resolve("replay.heat");
        try (ProjectDocument doc = ProjectDocument.create(work(), s)) {
            doc.saveAs(file);
        }
        try (ProjectDocument doc = ProjectDocument.open(file, work())) {
            assertThat(doc.settings()).isEqualTo(s);
        }
    }

    @Test
    void serverSettingSurvivesSaveAndOpen() throws Exception {
        Settings s = new Settings("host", 8585, 60, 1999).withCompetitionKey("WK").withRest(2222, false);
        Path file = dir.resolve("rest.heat");
        try (ProjectDocument doc = ProjectDocument.create(work(), s)) {
            doc.saveAs(file);
        }
        try (ProjectDocument doc = ProjectDocument.open(file, work())) {
            assertThat(doc.settings().restEnabled()).isFalse();
            assertThat(doc.settings().restPort()).isEqualTo(2222);
        }
    }

    @Test
    void projectFileOfAnEarlierVersionMapsServerPortZeroToOff() throws Exception {
        Path old = dir.resolve("oldrest.heat");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + old); var st = connection.createStatement()) {
            st.execute("create table settings (id integer primary key autoincrement, tcpHost varchar(255), tcpPort integer not null, competitionKey varchar(255), autoSaveSeconds integer not null, restPort integer not null)");
            st.execute("create table heat (id integer primary key autoincrement)");
            st.execute("create table rawdata (id integer primary key autoincrement)");
            st.execute("create table aresmessage (id integer primary key autoincrement)");
            st.execute("insert into settings (tcpHost, tcpPort, competitionKey, autoSaveSeconds, restPort) values ('timing', 9000, 'OLD', 30, 0)");
        }
        try (ProjectDocument doc = ProjectDocument.open(old, work())) {
            assertThat(doc.settings().restEnabled()).isFalse();
            assertThat(doc.settings().restPort()).isEqualTo(Settings.DEFAULT_REST_PORT);
            assertThat(doc.isDirty()).isFalse();
        }
    }

    @Test
    void projectFileOfTheFirstVersionOpensWithItsSettingsHeatRawDataAndMessages() throws Exception {
        Path file = Files.copy(Path.of("src/test/resources/compat/first-version.heat"), dir.resolve("first-version.heat"));
        ByteArrayOutputStream recorded = new ByteArrayOutputStream();
        recorded.writeBytes(DataHandlingMessageTestData.message1);
        recorded.writeBytes(DataHandlingMessageTestData.message2);
        try (ProjectDocument doc = ProjectDocument.open(file, work())) {
            assertThat(doc.isDirty()).isFalse();
            Settings s = doc.settings();
            assertThat(s.connectionType()).isEqualTo(ConnectionType.TCP);
            assertThat(s.tcpHost()).isEqualTo("localhost");
            assertThat(s.autoSaveSeconds()).isEqualTo(60);
            assertThat(s.restEnabled()).isFalse();
            assertThat(s.restPort()).isEqualTo(Settings.DEFAULT_REST_PORT);
            assertThat(s.replayFile()).isEmpty();
            assertThat(s.competitionKey()).isEqualTo("TestWK");

            HeatDto heat = new HeatStore(doc.database()).findRecent(1).get(0);
            assertThat(heat.competition()).isEqualTo("TestWK");
            assertThat(heat.event()).isEqualTo(1);
            assertThat(heat.heat()).isEqualTo(1);
            assertThat(heat.status()).isEqualTo(HeatStatus.Started);
            assertThat(heat.started()).isNotNull();
            assertThat(heat.ended()).isNull();
            assertThat(heat.lastMessage()).isNull();
            assertThat(heat.lanes()).hasSize(8).allMatch(l -> l.status() == LaneStatus.Used);

            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            RawDataRecorder.readAll(doc.database()).forEach(raw::writeBytes);
            assertThat(raw.toByteArray()).isEqualTo(recorded.toByteArray());
            assertThat(new MessageStore(doc.database()).findBy("TestWK", (short) 1, (byte) 1)).hasSize(1);

            doc.updateSettings(s.withTcp("other", 1));
            doc.saveAs(dir.resolve("resaved.heat"));
            assertThat(doc.isDirty()).isFalse();
        }
        try (ProjectDocument doc = ProjectDocument.open(dir.resolve("resaved.heat"), work())) {
            assertThat(doc.settings().tcpHost()).isEqualTo("other");
            assertThat(new HeatStore(doc.database()).findRecent(1).get(0).competition()).isEqualTo("TestWK");
        }
    }

    @Test
    void projectFileOfAnEarlierVersionWithoutConnectionColumnsOpensAsTcp() throws Exception {
        Path old = dir.resolve("old.heat");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + old); var st = connection.createStatement()) {
            st.execute("create table settings (id integer primary key autoincrement, tcpHost varchar(255), tcpPort integer not null, competitionKey varchar(255), autoSaveSeconds integer not null, restPort integer not null)");
            st.execute("create table heat (id integer primary key autoincrement)");
            st.execute("create table rawdata (id integer primary key autoincrement)");
            st.execute("create table aresmessage (id integer primary key autoincrement)");
            st.execute("insert into settings (tcpHost, tcpPort, competitionKey, autoSaveSeconds, restPort) values ('timing', 9000, 'OLD', 30, 0)");
        }
        try (ProjectDocument doc = ProjectDocument.open(old, work())) {
            assertThat(doc.settings()).isEqualTo(new Settings("timing", 9000, 30, 0).withCompetitionKey("OLD"));
            doc.updateSettings(doc.settings().withTcp("other", 1));
            assertThat(doc.settings().tcpHost()).isEqualTo("other");
        }
    }

    @Test
    void theCompetitionKeyIsSavedOncePerProjectFile() throws Exception {
        Path file = dir.resolve("keyed.timing");
        try (ProjectDocument doc = ProjectDocument.create(work(), Settings.defaults().withCompetitionKey("WK 2026"))) {
            doc.saveAs(file);
        }
        try (ProjectDocument doc = ProjectDocument.open(file, work())) {
            assertThat(doc.settings().competitionKey()).isEqualTo("WK 2026");
            doc.updateSettings(doc.settings().withCompetitionKey("DM 2026"));
            doc.save();
        }
        try (ProjectDocument doc = ProjectDocument.open(file, work())) {
            assertThat(doc.settings().competitionKey()).isEqualTo("DM 2026");
        }
    }
}
