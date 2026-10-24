package org.lisasp.alphatiming.ui.core.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatiming.project.db.InvalidProjectFileException;
import org.lisasp.alphatiming.project.db.ProjectFileInUseException;
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
import java.nio.file.attribute.FileTime;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

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
        try (ProjectDocument recovered = ProjectDocument.recover(found.get(0).workFile())) {
            assertThat(recovered.settings()).isEqualTo(SETTINGS);
            assertThat(recovered.isDirty()).isTrue();
            assertThat(recovered.file()).isEmpty();
        }
        assertThat(ProjectDocument.findRecoverable(work())).isEmpty();
    }

    @Test
    void leftoverNamesTheProjectFileItBelongsTo() throws Exception {
        Path file = dir.resolve("crashed.timing");
        try (ProjectDocument doc = ProjectDocument.create(work(), SETTINGS)) {
            doc.saveAs(file);
            doc.database().close();
            var found = ProjectDocument.findRecoverable(work());
            assertThat(found).hasSize(1);
            CrashLeftover leftover = found.get(0);
            assertThat(leftover.projectFile()).contains(file);
            assertThat(leftover.lastModified()).isEqualTo(Files.getLastModifiedTime(leftover.workFile()).toInstant());
        }
    }

    private Path leftoverOf(Path projectFile) throws IOException {
        ProjectDocument crashed = ProjectDocument.create(work(), SETTINGS);
        Path workFile = crashed.database().workFile();
        WorkFileOrigin.record(workFile, projectFile);
        crashed.database().close();
        return workFile;
    }

    @Test
    void recoveredDocumentKeepsItsUnchangedProjectFile() throws Exception {
        Path file = savedProject("x.timing");
        try (ProjectDocument recovered = ProjectDocument.recover(leftoverOf(file))) {
            assertThat(recovered.file()).contains(file);
            assertThatThrownBy(() -> ProjectDocument.open(file, work())).isInstanceOf(ProjectFileInUseException.class);
        }
    }

    @Test
    void recoveredDocumentDropsAProjectFileSavedAfterTheCrash() throws Exception {
        Path file = savedProject("x.timing");
        Path leftover = leftoverOf(file);
        Files.setLastModifiedTime(file, FileTime.from(Files.getLastModifiedTime(file).toInstant().plusSeconds(60)));
        try (ProjectDocument recovered = ProjectDocument.recover(leftover)) {
            assertThat(recovered.file()).isEmpty();
            assertThat(recovered.isDirty()).isTrue();
            ProjectDocument.open(file, work()).close();
        }
    }

    @Test
    void recoveredDocumentDropsAProjectFileOpenElsewhere() throws Exception {
        Path file = savedProject("x.timing");
        Path leftover = leftoverOf(file);
        try (ProjectDocument elsewhere = ProjectDocument.open(file, work());
             ProjectDocument recovered = ProjectDocument.recover(leftover)) {
            assertThat(recovered.file()).isEmpty();
            assertThat(recovered.isDirty()).isTrue();
        }
    }

    @Test
    void aFailedRecoveryReleasesEverythingAndKeepsTheLeftover() throws Exception {
        Path file = savedProject("x.timing");
        Path leftover = leftoverOf(file);
        corruptSettingsTable(leftover);
        assertThatThrownBy(() -> ProjectDocument.recover(leftover)).isInstanceOf(RuntimeException.class);
        assertThat(ProjectDocument.findRecoverable(work())).singleElement()
                                                           .satisfies(l -> assertThat(l.workFile()).isEqualTo(leftover))
                                                           .satisfies(l -> assertThat(l.projectFile()).contains(file));
        ProjectDocument.open(file, work()).close();
        ProjectDocument.discard(leftover);
        assertThat(workFiles()).isZero();
    }

    /** Leaves the schema intact, so adopting the work file succeeds and only reading the settings fails. */
    private static void corruptSettingsTable(Path workFile) throws Exception {
        long offset;
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + workFile); var st = connection.createStatement();
             var page = st.executeQuery("select rootpage, (select page_size from pragma_page_size) from sqlite_master where name = 'settings'")) {
            offset = (page.getLong(1) - 1) * page.getLong(2);
        }
        try (var channel = java.nio.channels.FileChannel.open(workFile, java.nio.file.StandardOpenOption.WRITE)) {
            byte[] garbage = new byte[512];
            java.util.Arrays.fill(garbage, (byte) 0x7f);
            channel.write(java.nio.ByteBuffer.wrap(garbage), offset);
        }
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
        ProjectDocument.discard(ProjectDocument.findRecoverable(work()).get(0).workFile());
        assertThat(workFiles()).isZero();
    }

    @Test
    void discardRemovesTheNoteOfTheProjectFile() throws Exception {
        Path leftover = leftoverOf(savedProject("x.timing"));
        ProjectDocument.discard(leftover);
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

    private static final List<String> FIRST_VERSION_TABLES = List.of("settings", "rawdata", "aresmessage", "lanetime", "heat", "lane");

    private static Map<String, List<List<Object>>> contentOf(Path file, Map<String, List<String>> columns) throws SQLException {
        Map<String, List<List<Object>>> content = new LinkedHashMap<>();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file); var st = connection.createStatement()) {
            for (var table : columns.entrySet()) {
                List<List<Object>> rows = new ArrayList<>();
                try (var rs = st.executeQuery("select " + String.join(", ", table.getValue()) + " from " + table.getKey() + " order by id")) {
                    while (rs.next()) {
                        List<Object> row = new ArrayList<>();
                        for (int i = 1; i <= table.getValue().size(); i++) {
                            Object value = rs.getObject(i);
                            row.add(value instanceof byte[] bytes ? HexFormat.of().formatHex(bytes) : value);
                        }
                        rows.add(row);
                    }
                }
                content.put(table.getKey(), rows);
            }
        }
        return content;
    }

    private static Map<String, List<String>> columnsOf(Path file) throws SQLException {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file); var st = connection.createStatement()) {
            for (String table : FIRST_VERSION_TABLES) {
                List<String> names = new ArrayList<>();
                try (var rs = st.executeQuery("select name from pragma_table_info('" + table + "')")) {
                    while (rs.next()) {
                        names.add(rs.getString(1));
                    }
                }
                columns.put(table, names);
            }
        }
        return columns;
    }

    private static List<String> schemaOf(Path file) throws SQLException {
        List<String> sql = new ArrayList<>();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file); var st = connection.createStatement();
             var rs = st.executeQuery("select sql from sqlite_master where sql is not null")) {
            while (rs.next()) {
                sql.add(rs.getString(1));
            }
        }
        return sql;
    }

    @Test
    void projectFileOfTheFirstVersionLosesItsEnumChecksAndKeepsItsData() throws Exception {
        Path file = Files.copy(Path.of("src/test/resources/compat/first-version.heat"), dir.resolve("first-version.heat"));
        Map<String, List<String>> columns = columnsOf(file);
        Map<String, List<List<Object>>> before = contentOf(file, columns);
        assertThat(schemaOf(file)).anyMatch(sql -> sql.contains("check"));

        try (ProjectDocument doc = ProjectDocument.open(file, work())) {
            Path workFile = doc.database().workFile();
            assertThat(schemaOf(workFile)).noneMatch(sql -> sql.toLowerCase(Locale.ROOT).contains("check"));
            assertThat(contentOf(workFile, columns)).isEqualTo(before);

            doc.database().write(em -> {
                em.createNativeQuery("update heat set status = 'NewStatus'").executeUpdate();
                em.createNativeQuery("update lane set penalty = 'NewPenalty', status = 'NewStatus'").executeUpdate();
                em.createNativeQuery("update aresmessage set messageType = 'New', kindOfTime = 'New', timeType = 'New', rankInfo = 'New', timeInfo = 'New', timeMarker = 'New'").executeUpdate();
                em.createNativeQuery("insert into lanetime (event, heatNumber, laneIndex, timeInMillis, status) values (1, 1, 0, 0, 'New')").executeUpdate();
                return null;
            });
            assertThat(contentOf(workFile, Map.of("heat", List.of("status"), "lanetime", List.of("status"))))
                    .containsEntry("heat", List.of(List.of("NewStatus")))
                    .containsEntry("lanetime", List.of(List.of("New")));
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

    private Path savedProject(String name) throws IOException {
        Path file = dir.resolve(name);
        try (ProjectDocument doc = ProjectDocument.create(work(), SETTINGS)) {
            doc.saveAs(file);
        }
        return file;
    }

    @Test
    void aProjectFileOpenInOneDocumentIsRefusedToAnother() throws Exception {
        Path file = savedProject("x.timing");
        try (ProjectDocument first = ProjectDocument.open(file, work())) {
            assertThatThrownBy(() -> ProjectDocument.open(file, work())).isInstanceOfSatisfying(ProjectFileInUseException.class, ex -> assertThat(ex.file()).isEqualTo(file));
            assertThat(workFiles()).isEqualTo(3); // the first document's work file, its lock and its project note
        }
    }

    @Test
    void closingTheDocumentReleasesTheProjectFileAndRemovesItsLockFile() throws Exception {
        Path file = savedProject("x.timing");
        Path lock = dir.resolve("x.timing.lock");
        try (ProjectDocument doc = ProjectDocument.open(file, work())) {
            assertThat(lock).exists();
        }
        assertThat(lock).doesNotExist();
        try (ProjectDocument again = ProjectDocument.open(file, work())) {
            assertThat(again.file()).contains(file);
        }
    }

    @Test
    void saveAsToAProjectFileOpenElsewhereIsRefusedAndLeavesItUntouched() throws Exception {
        Path file = savedProject("x.timing");
        byte[] before = Files.readAllBytes(file);
        try (ProjectDocument holder = ProjectDocument.open(file, work());
             ProjectDocument other = ProjectDocument.create(work(), SETTINGS)) {
            other.updateSettings(SETTINGS.withTcp("changed", 1));
            assertThatThrownBy(() -> other.saveAs(file)).isInstanceOfSatisfying(ProjectFileInUseException.class, ex -> assertThat(ex.file()).isEqualTo(file));
            assertThat(Files.readAllBytes(file)).isEqualTo(before);
            assertThat(other.file()).isEmpty();
            assertThat(other.isDirty()).isTrue();
        }
    }

    @Test
    void saveAsMovesTheLockToTheNewFile() throws Exception {
        Path a = savedProject("a.timing");
        Path b = dir.resolve("b.timing");
        try (ProjectDocument doc = ProjectDocument.open(a, work())) {
            doc.saveAs(b);
            assertThatThrownBy(() -> ProjectDocument.open(b, work())).isInstanceOf(ProjectFileInUseException.class);
            assertThat(dir.resolve("a.timing.lock")).doesNotExist();
            try (ProjectDocument other = ProjectDocument.open(a, work())) {
                assertThat(other.file()).contains(a);
            }
        }
    }

    @Test
    void saveAsToItsOwnFileKeepsTheLock() throws Exception {
        Path file = savedProject("x.timing");
        try (ProjectDocument doc = ProjectDocument.open(file, work())) {
            doc.saveAs(file);
            assertThat(doc.file()).contains(file);
            assertThatThrownBy(() -> ProjectDocument.open(file, work())).isInstanceOf(ProjectFileInUseException.class);
        }
    }

    @Test
    void aLockFileLeftBehindByACrashDoesNotBlockOpening() throws Exception {
        Path file = savedProject("x.timing");
        Files.writeString(dir.resolve("x.timing.lock"), "");
        try (ProjectDocument doc = ProjectDocument.open(file, work())) {
            assertThat(doc.file()).contains(file);
        }
    }

    @Test
    void closeWaitsForARunningSaveAndLeavesNoOriginNote() throws Exception {
        Path file = dir.resolve("x.timing");
        ProjectDocument doc = ProjectDocument.create(work(), SETTINGS);
        doc.saveAs(file);
        doc.updateSettings(SETTINGS.withTcp("changed", 1));
        CountDownLatch saveBlocked = new CountDownLatch(1);
        CountDownLatch releaseSave = new CountDownLatch(1);
        AtomicReference<Exception> saveFailure = new AtomicReference<>();
        Thread saver = new Thread(() -> {
            try {
                doc.save();
            } catch (Exception ex) {
                saveFailure.set(ex);
            }
        });
        doc.addDirtyListener(dirty -> {
            if (!dirty && Thread.currentThread() == saver) {
                saveBlocked.countDown();
                awaitQuietly(releaseSave);
            }
        });
        saver.start();
        saveBlocked.await();
        Thread closer = new Thread(doc::close);
        closer.start();
        awaitParkedOrTerminated(closer);
        boolean closeWaited = closer.isAlive();
        releaseSave.countDown();
        saver.join();
        closer.join();

        assertThat(closeWaited).isTrue();
        assertThat(saveFailure.get()).isNull();
        assertThat(WorkFileOrigin.read(doc.database().workFile())).isEmpty();
        assertThat(doc.database().workFile()).doesNotExist();
    }

    @Test
    void aSaveWaitingForSaveAsWritesToTheNewFile() throws Exception {
        Path a = dir.resolve("a.timing");
        Path b = dir.resolve("b.timing");
        try (ProjectDocument doc = ProjectDocument.create(work(), SETTINGS)) {
            doc.saveAs(a);
            doc.updateSettings(SETTINGS.withTcp("changed", 1));
            CountDownLatch saveAsBlocked = new CountDownLatch(1);
            CountDownLatch releaseSaveAs = new CountDownLatch(1);
            List<Exception> failures = new CopyOnWriteArrayList<>();
            Thread savingAs = new Thread(() -> {
                try {
                    doc.saveAs(b);
                } catch (Exception ex) {
                    failures.add(ex);
                }
            });
            doc.addDirtyListener(dirty -> {
                if (!dirty && Thread.currentThread() == savingAs) {
                    saveAsBlocked.countDown();
                    awaitQuietly(releaseSaveAs);
                }
            });
            savingAs.start();
            saveAsBlocked.await();
            Thread saving = new Thread(() -> {
                try {
                    doc.save();
                } catch (Exception ex) {
                    failures.add(ex);
                }
            });
            saving.start();
            awaitParkedOrTerminated(saving);
            releaseSaveAs.countDown();
            savingAs.join();
            saving.join();

            assertThat(failures).isEmpty();
            assertThat(ProjectDatabase.previousVersionOf(a)).doesNotExist();
            assertThat(ProjectDatabase.previousVersionOf(b)).exists();
            assertThat(WorkFileOrigin.read(doc.database().workFile())).map(WorkFileOrigin::projectFile).contains(b.toAbsolutePath());
        }
    }

    @Test
    void aClosedDocumentRefusesToSave() throws Exception {
        ProjectDocument doc = ProjectDocument.create(work(), SETTINGS);
        doc.saveAs(dir.resolve("x.timing"));
        doc.updateSettings(SETTINGS.withTcp("changed", 1));
        doc.close();

        assertThatThrownBy(doc::save).isInstanceOf(IllegalStateException.class);
        assertThat(ProjectDatabase.previousVersionOf(dir.resolve("x.timing"))).doesNotExist();
    }

    static void awaitParkedOrTerminated(Thread thread) {
        await().until(() -> thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TERMINATED);
    }

    static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
