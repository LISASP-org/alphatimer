package org.lisasp.alphatiming.ui.core.project;

import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatiming.project.db.InvalidProjectFileException;
import org.lisasp.alphatiming.project.db.ProjectFileInUseException;
import org.lisasp.alphatiming.project.db.TimingDatabase;
import org.lisasp.alphatiming.project.db.TimingFileWriter;
import org.lisasp.alphatiming.project.db.TimingWorkFolder;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatService;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.TestMessageCreator;
import org.lisasp.alphatiming.project.db.CrashLeftover;
import org.lisasp.alphatiming.project.db.HeatStore;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.project.db.MessageStore;
import org.lisasp.alphatiming.project.db.RawDataRecorder;
import org.lisasp.alphatiming.project.db.SettingsEntity;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.SerialMode;
import org.lisasp.alphatiming.ui.core.settings.Settings;
import org.lisasp.alphatiming.project.db.testdata.DataHandlingMessageTestData;
import org.sqlite.ProgressHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class TimingDocumentTest {

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
        try (TimingDocument doc = TimingDocument.create(work(), SETTINGS)) {
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
        try (TimingDocument doc = TimingDocument.create(work(), SETTINGS)) {
            doc.saveAs(file);
        }
        assertThat(workFiles()).isZero();
        try (TimingDocument doc = TimingDocument.open(file, work())) {
            assertThat(doc.settings()).isEqualTo(SETTINGS);
            assertThat(doc.isDirty()).isFalse();
            assertThat(doc.file()).contains(file);
        }
        assertThat(workFiles()).isZero();
    }

    @Test
    void saveWithoutFileIsRejected() throws Exception {
        try (TimingDocument doc = TimingDocument.create(work(), SETTINGS)) {
            assertThatThrownBy(doc::save).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void failedSaveKeepsDocumentDirtyAndLeavesNoTempFile() throws Exception {
        try (TimingDocument doc = TimingDocument.create(work(), SETTINGS)) {
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
        try (TimingDocument doc = TimingDocument.create(work(), SETTINGS)) {
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
        assertThatThrownBy(() -> TimingDocument.open(bad, work())).isInstanceOfSatisfying(InvalidProjectFileException.class, ex -> assertThat(ex.file()).isEqualTo(bad));
        assertThat(workFiles()).isZero();
    }

    @Test
    void openRejectsForeignSqliteFileAndDoesNotTouchIt() throws Exception {
        Path other = dir.resolve("other.sqlite");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + other); var st = connection.createStatement()) {
            st.execute("create table results (id integer primary key, name text)");
        }
        long size = Files.size(other);
        assertThatThrownBy(() -> TimingDocument.open(other, work())).isInstanceOf(InvalidProjectFileException.class);
        assertThat(workFiles()).isZero();
        assertThat(Files.size(other)).isEqualTo(size);
    }

    @Test
    void crashLeftoversCanBeFoundAndRecovered() throws Exception {
        TimingDocument doc = TimingDocument.create(work(), SETTINGS);
        doc.database().close(); // simulate a crash: the work file stays
        var found = TimingWorkFolder.findLeftovers(work());
        assertThat(found).hasSize(1);
        try (TimingDocument recovered = TimingDocument.recover(found.get(0).workFile())) {
            assertThat(recovered.settings()).isEqualTo(SETTINGS);
            assertThat(recovered.isDirty()).isTrue();
            assertThat(recovered.file()).isEmpty();
        }
        assertThat(TimingWorkFolder.findLeftovers(work())).isEmpty();
    }

    @Test
    void leftoverNamesTheProjectFileItBelongsTo() throws Exception {
        Path file = dir.resolve("crashed.timing");
        try (TimingDocument doc = TimingDocument.create(work(), SETTINGS)) {
            doc.saveAs(file);
            doc.database().close();
            var found = TimingWorkFolder.findLeftovers(work());
            assertThat(found).hasSize(1);
            CrashLeftover leftover = found.get(0);
            assertThat(leftover.projectFile()).contains(file);
            assertThat(leftover.lastModified()).isEqualTo(Files.getLastModifiedTime(leftover.workFile()).toInstant());
        }
    }

    private Path leftoverOf(Path projectFile) throws IOException {
        TimingDocument crashed = TimingDocument.create(work(), SETTINGS);
        Path workFile = crashed.database().workFile();
        TimingWorkFolder.recordOrigin(workFile, projectFile);
        crashed.database().close();
        return workFile;
    }

    @Test
    void recoveredDocumentKeepsItsUnchangedProjectFile() throws Exception {
        Path file = savedProject("x.timing");
        try (TimingDocument recovered = TimingDocument.recover(leftoverOf(file))) {
            assertThat(recovered.file()).contains(file);
            assertThatThrownBy(() -> TimingDocument.open(file, work())).isInstanceOf(ProjectFileInUseException.class);
        }
    }

    @Test
    void recoveredDocumentDropsAProjectFileSavedAfterTheCrash() throws Exception {
        Path file = savedProject("x.timing");
        Path leftover = leftoverOf(file);
        Files.setLastModifiedTime(file, FileTime.from(Files.getLastModifiedTime(file).toInstant().plusSeconds(60)));
        try (TimingDocument recovered = TimingDocument.recover(leftover)) {
            assertThat(recovered.file()).isEmpty();
            assertThat(recovered.isDirty()).isTrue();
            TimingDocument.open(file, work()).close();
        }
    }

    @Test
    void recoveredDocumentDropsAProjectFileOpenElsewhere() throws Exception {
        Path file = savedProject("x.timing");
        Path leftover = leftoverOf(file);
        try (TimingDocument elsewhere = TimingDocument.open(file, work());
             TimingDocument recovered = TimingDocument.recover(leftover)) {
            assertThat(recovered.file()).isEmpty();
            assertThat(recovered.isDirty()).isTrue();
        }
    }

    @Test
    void aFailedRecoveryReleasesEverythingAndKeepsTheLeftover() throws Exception {
        Path file = savedProject("x.timing");
        Path leftover = leftoverOf(file);
        corruptSettingsTable(leftover);
        assertThatThrownBy(() -> TimingDocument.recover(leftover)).isInstanceOf(RuntimeException.class);
        assertThat(TimingWorkFolder.findLeftovers(work())).singleElement()
                                                           .satisfies(l -> assertThat(l.workFile()).isEqualTo(leftover))
                                                           .satisfies(l -> assertThat(l.projectFile()).contains(file));
        TimingDocument.open(file, work()).close();
        TimingWorkFolder.discard(leftover);
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
    void serialSettingsSurviveSaveAndOpen() throws Exception {
        Settings serial = new Settings(ConnectionType.SERIAL, "host", 8585, "COM3", SerialMode.QUANTUM, 60, 1999, true).withCompetitionKey("WK");
        Path file = dir.resolve("serial.heat");
        try (TimingDocument doc = TimingDocument.create(work(), serial)) {
            doc.saveAs(file);
        }
        try (TimingDocument doc = TimingDocument.open(file, work())) {
            assertThat(doc.settings()).isEqualTo(serial);
        }
    }

    @Test
    void replayFileSurvivesSaveAndOpen() throws Exception {
        Settings s = new Settings("host", 8585, 60, 1999).withCompetitionKey("WK").withConnectionType(ConnectionType.FILE).withReplayFile("/data/run.serial");
        Path file = dir.resolve("replay.heat");
        try (TimingDocument doc = TimingDocument.create(work(), s)) {
            doc.saveAs(file);
        }
        try (TimingDocument doc = TimingDocument.open(file, work())) {
            assertThat(doc.settings()).isEqualTo(s);
        }
    }

    @Test
    void serverSettingSurvivesSaveAndOpen() throws Exception {
        Settings s = new Settings("host", 8585, 60, 1999).withCompetitionKey("WK").withRest(2222, false);
        Path file = dir.resolve("rest.heat");
        try (TimingDocument doc = TimingDocument.create(work(), s)) {
            doc.saveAs(file);
        }
        try (TimingDocument doc = TimingDocument.open(file, work())) {
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
        try (TimingDocument doc = TimingDocument.open(old, work())) {
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
        try (TimingDocument doc = TimingDocument.open(file, work())) {
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
        try (TimingDocument doc = TimingDocument.open(dir.resolve("resaved.heat"), work())) {
            assertThat(doc.settings().tcpHost()).isEqualTo("other");
            assertThat(new HeatStore(doc.database()).find(1, 1)).isPresent();
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

    private static List<String> uniqueKeysOfHeat(Path file) throws SQLException {
        List<String> keys = new ArrayList<>();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file); var st = connection.createStatement();
             var rs = st.executeQuery("select group_concat(ii.name, ',') from pragma_index_list('heat') il, pragma_index_info(il.name) ii "
                     + "where il.\"unique\" = 1 group by il.name")) {
            while (rs.next()) {
                keys.add(rs.getString(1));
            }
        }
        return keys;
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
    void projectFileOfTheFirstVersionLosesItsEnumChecksAndTheHeatCompetitionAndKeepsItsData() throws Exception {
        Path file = Files.copy(Path.of("src/test/resources/compat/first-version.heat"), dir.resolve("first-version.heat"));
        Map<String, List<String>> columns = columnsOf(file);
        assertThat(columns.get("heat").remove("competition")).isTrue();
        Map<String, List<List<Object>>> before = contentOf(file, columns);
        assertThat(schemaOf(file)).anyMatch(sql -> sql.contains("check"));

        try (TimingDocument doc = TimingDocument.open(file, work())) {
            Path workFile = doc.database().workFile();
            assertThat(schemaOf(workFile)).noneMatch(sql -> sql.toLowerCase(Locale.ROOT).contains("check"));
            assertThat(columnsOf(workFile).get("heat")).doesNotContain("competition");
            assertThat(uniqueKeysOfHeat(workFile)).containsExactly("event,heatNumber");
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
    void recordingIntoAProjectFileOfTheFirstVersionUpdatesItsTaggedHeat() throws Exception {
        Path file = Files.copy(Path.of("src/test/resources/compat/first-version.heat"), dir.resolve("first-version.heat"));
        try (TimingDocument doc = TimingDocument.open(file, work())) {
            HeatService heats = new HeatService(new HeatStore(doc.database()));

            heats.accept(TestMessageCreator.split(1, 1, 3, 1, 12_340));

            Path workFile = doc.database().workFile();
            assertThat(contentOf(workFile, Map.of("heat", List.of("id", "event", "heatNumber")))).containsEntry("heat", List.of(List.of(1, 1, 1)));
            assertThat(contentOf(workFile, Map.of("lane", List.of("id", "heat_id", "number", "timeInMillis"))).get("lane"))
                    .hasSize(8)
                    .contains(List.of(3, 1, 3, 12_340));
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
        try (TimingDocument doc = TimingDocument.open(old, work())) {
            assertThat(doc.settings()).isEqualTo(new Settings("timing", 9000, 30, 0).withCompetitionKey("OLD"));
            doc.updateSettings(doc.settings().withTcp("other", 1));
            assertThat(doc.settings().tcpHost()).isEqualTo("other");
        }
    }

    @Test
    void theCompetitionKeyIsSavedOncePerProjectFile() throws Exception {
        Path file = dir.resolve("keyed.timing");
        try (TimingDocument doc = TimingDocument.create(work(), Settings.defaults().withCompetitionKey("WK 2026"))) {
            doc.saveAs(file);
        }
        try (TimingDocument doc = TimingDocument.open(file, work())) {
            assertThat(doc.settings().competitionKey()).isEqualTo("WK 2026");
            doc.updateSettings(doc.settings().withCompetitionKey("DM 2026"));
            doc.save();
        }
        try (TimingDocument doc = TimingDocument.open(file, work())) {
            assertThat(doc.settings().competitionKey()).isEqualTo("DM 2026");
        }
    }

    private Path savedProject(String name) throws IOException {
        Path file = dir.resolve(name);
        try (TimingDocument doc = TimingDocument.create(work(), SETTINGS)) {
            doc.saveAs(file);
        }
        return file;
    }

    @Test
    void aProjectFileOpenInOneDocumentIsRefusedToAnother() throws Exception {
        Path file = savedProject("x.timing");
        try (TimingDocument first = TimingDocument.open(file, work())) {
            assertThatThrownBy(() -> TimingDocument.open(file, work())).isInstanceOfSatisfying(ProjectFileInUseException.class, ex -> assertThat(ex.file()).isEqualTo(file));
            assertThat(workFiles()).isEqualTo(3); // the first document's work file, its lock and its project note
        }
    }

    @Test
    void closingTheDocumentReleasesTheProjectFileAndRemovesItsLockFile() throws Exception {
        Path file = savedProject("x.timing");
        Path lock = dir.resolve("x.timing.lock");
        try (TimingDocument doc = TimingDocument.open(file, work())) {
            assertThat(lock).exists();
        }
        assertThat(lock).doesNotExist();
        try (TimingDocument again = TimingDocument.open(file, work())) {
            assertThat(again.file()).contains(file);
        }
    }

    @Test
    void saveAsToAProjectFileOpenElsewhereIsRefusedAndLeavesItUntouched() throws Exception {
        Path file = savedProject("x.timing");
        byte[] before = Files.readAllBytes(file);
        try (TimingDocument holder = TimingDocument.open(file, work());
             TimingDocument other = TimingDocument.create(work(), SETTINGS)) {
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
        try (TimingDocument doc = TimingDocument.open(a, work())) {
            doc.saveAs(b);
            assertThatThrownBy(() -> TimingDocument.open(b, work())).isInstanceOf(ProjectFileInUseException.class);
            assertThat(dir.resolve("a.timing.lock")).doesNotExist();
            try (TimingDocument other = TimingDocument.open(a, work())) {
                assertThat(other.file()).contains(a);
            }
        }
    }

    @Test
    void saveAsToItsOwnFileKeepsTheLock() throws Exception {
        Path file = savedProject("x.timing");
        try (TimingDocument doc = TimingDocument.open(file, work())) {
            doc.saveAs(file);
            assertThat(doc.file()).contains(file);
            assertThatThrownBy(() -> TimingDocument.open(file, work())).isInstanceOf(ProjectFileInUseException.class);
        }
    }

    @Test
    void aLockFileLeftBehindByACrashDoesNotBlockOpening() throws Exception {
        Path file = savedProject("x.timing");
        Files.writeString(dir.resolve("x.timing.lock"), "");
        try (TimingDocument doc = TimingDocument.open(file, work())) {
            assertThat(doc.file()).contains(file);
        }
    }

    @Test
    void closeWaitsForARunningSaveAndLeavesNoOriginNote() throws Exception {
        Path file = dir.resolve("x.timing");
        TimingDocument doc = TimingDocument.create(work(), SETTINGS);
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
        assertThat(TimingWorkFolder.origin(doc.database().workFile())).isEmpty();
        assertThat(doc.database().workFile()).doesNotExist();
    }

    @Test
    void aSaveWaitingForSaveAsWritesToTheNewFile() throws Exception {
        Path a = dir.resolve("a.timing");
        Path b = dir.resolve("b.timing");
        try (TimingDocument doc = TimingDocument.create(work(), SETTINGS)) {
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
            assertThat(TimingFileWriter.previousVersionOf(a)).doesNotExist();
            assertThat(TimingFileWriter.previousVersionOf(b)).exists();
            assertThat(TimingWorkFolder.origin(doc.database().workFile())).map(TimingWorkFolder.Origin::projectFile).contains(b.toAbsolutePath());
        }
    }

    @Test
    void aClosedDocumentRefusesToSave() throws Exception {
        TimingDocument doc = TimingDocument.create(work(), SETTINGS);
        doc.saveAs(dir.resolve("x.timing"));
        doc.updateSettings(SETTINGS.withTcp("changed", 1));
        doc.close();

        assertThatThrownBy(doc::save).isInstanceOf(IllegalStateException.class);
        assertThat(TimingFileWriter.previousVersionOf(dir.resolve("x.timing"))).doesNotExist();
    }

    private static void saveWithSettings(TimingDocument doc, Path file, int count) throws IOException {
        doc.database().write(em -> {
            em.createQuery("delete from SettingsEntity").executeUpdate();
            for (int i = 0; i < count; i++) {
                em.persist(new SettingsEntity("h", 1, 60, 0));
            }
            return null;
        });
        doc.saveAs(file);
    }

    private static void addSettings(TimingDocument doc) {
        doc.database().write(em -> {
            em.persist(new SettingsEntity("h", 1, 60, 0));
            return null;
        });
    }

    private long settingsIn(Path file) throws IOException {
        try (TimingDatabase db = TimingDatabase.openCopy(file, dir)) {
            long count = db.read(em -> em.createQuery("select count(s) from SettingsEntity s", Long.class).getSingleResult());
            db.closeAndDelete();
            return count;
        }
    }

    @Test
    void aSaveStartedDuringAnotherSaveOfTheSameFileWaitsForIt() throws Exception {
        Path projects = Files.createDirectory(dir.resolve("projects"));
        Path file = projects.resolve("wk.timing");
        Path previous = projects.resolve("wk-previous.timing");
        try (TimingDocument doc = TimingDocument.create(work(), SETTINGS)) {
            saveWithSettings(doc, file, 1);
            addSettings(doc);
            CountDownLatch tempFileWritten = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            pauseWhenTempFileAppears(doc.database(), work(), tempFileWritten, release);

            FutureTask<Void> first = saveTask(doc);
            new Thread(first).start();
            assertThat(tempFileWritten.await(20, TimeUnit.SECONDS)).isTrue();
            FutureTask<Void> second = saveTask(doc);
            Thread secondThread = new Thread(second);
            secondThread.start();
            await().atMost(20, TimeUnit.SECONDS).until(() -> secondThread.getState() == Thread.State.WAITING);
            release.countDown();

            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
        try (var files = Files.list(projects)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder("wk.timing", "wk-previous.timing");
        }
        assertThat(settingsIn(previous)).isEqualTo(2);
        assertThat(settingsIn(file)).isEqualTo(2);
    }

    @Test
    void aWriteDuringTheMoveOfASaveIsNotBlocked() throws Exception {
        Path projects = Files.createDirectory(dir.resolve("projects"));
        Path file = projects.resolve("wk.timing");
        try (TimingDocument doc = TimingDocument.create(work(), SETTINGS)) {
            saveWithSettings(doc, file, 1);
            addSettings(doc);
            CountDownLatch moving = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            doc.useFileWriter(new TimingFileWriter((source, target) -> {
                moving.countDown();
                awaitQuietly(release);
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            }));
            var pool = Executors.newFixedThreadPool(2);
            try {
                Future<Void> save = pool.submit(() -> {
                    doc.save();
                    return null;
                });
                assertThat(moving.await(20, TimeUnit.SECONDS)).isTrue();

                pool.submit(() -> addSettings(doc)).get(20, TimeUnit.SECONDS);
                assertThat(save).isNotDone();
                release.countDown();

                save.get(20, TimeUnit.SECONDS);
                assertThat(doc.isDirty()).isTrue();
            } finally {
                release.countDown();
                pool.shutdown();
            }
        }
        assertThat(settingsIn(file)).isEqualTo(2);
        assertThat(settingsIn(projects.resolve("wk-previous.timing"))).isEqualTo(1);
    }

    private static FutureTask<Void> saveTask(TimingDocument doc) {
        return new FutureTask<>(() -> {
            doc.save();
            return null;
        });
    }

    private static void pauseWhenTempFileAppears(TimingDatabase db, Path workDir, CountDownLatch reached, CountDownLatch release) {
        AtomicBoolean paused = new AtomicBoolean();
        db.read(em -> {
            em.unwrap(Session.class).doWork(connection -> ProgressHandler.setHandler(connection, 1, new ProgressHandler() {
                @Override
                protected int progress() {
                    if (!paused.get() && hasTempFile(workDir)) {
                        paused.set(true);
                        reached.countDown();
                        awaitQuietly(release);
                    }
                    return 0;
                }
            }));
            return null;
        });
    }

    private static boolean hasTempFile(Path directory) {
        try (var files = Files.list(directory)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith(".tmp"));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
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
