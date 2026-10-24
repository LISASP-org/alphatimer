package org.lisasp.alphatiming.ui.core.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSettingsStoreTest {

    @TempDir
    Path dir;

    @Test
    void roundTripCreatesParentDirectories() {
        DefaultSettingsStore store = new DefaultSettingsStore(dir.resolve("a/b/defaults.json"));
        Settings s = new Settings("host", 1234, 30, 2000).withCompetitionKey("WK");
        store.save(s);
        assertThat(store.load()).isEqualTo(s);
    }

    @Test
    void missingFileGivesBuiltInDefaults() {
        assertThat(new DefaultSettingsStore(dir.resolve("none.json")).load()).isEqualTo(Settings.defaults());
    }

    @Test
    void corruptFileGivesBuiltInDefaults() throws Exception {
        Path file = dir.resolve("defaults.json");
        Files.writeString(file, "{not json");
        assertThat(new DefaultSettingsStore(file).load()).isEqualTo(Settings.defaults());
    }

    @Test
    void unwritableLocationDoesNotThrow() throws Exception {
        Path blocker = dir.resolve("file");
        Files.writeString(blocker, "x");
        new DefaultSettingsStore(blocker.resolve("sub/defaults.json")).save(Settings.defaults());
    }

    @Test
    void serialSettingsRoundTrip() {
        DefaultSettingsStore store = new DefaultSettingsStore(dir.resolve("d.json"));
        Settings s = new Settings(ConnectionType.SERIAL, "", 8585, "/dev/ttyUSB1", SerialMode.QUANTUM, 30, 1999, false).withCompetitionKey("WK");
        store.save(s);
        assertThat(store.load()).isEqualTo(s);
    }

    @Test
    void earlierDefaultsMapPortZeroToServerOffAndOtherPortsToServerOn() throws Exception {
        Path off = dir.resolve("off.json");
        Files.writeString(off, "{\"tcpHost\":\"timing\",\"tcpPort\":9000,\"autoSaveSeconds\":30,\"restPort\":0}");
        Settings loadedOff = new DefaultSettingsStore(off).load();
        assertThat(loadedOff.restEnabled()).isFalse();
        assertThat(loadedOff.restPort()).isEqualTo(Settings.DEFAULT_REST_PORT);
        Path on = dir.resolve("on.json");
        Files.writeString(on, "{\"tcpHost\":\"timing\",\"tcpPort\":9000,\"autoSaveSeconds\":30,\"restPort\":2000}");
        assertThat(new DefaultSettingsStore(on).load().restEnabled()).isTrue();
        assertThat(new DefaultSettingsStore(on).load().restPort()).isEqualTo(2000);
    }

    @Test
    void serverSettingRoundTrips() {
        DefaultSettingsStore store = new DefaultSettingsStore(dir.resolve("r.json"));
        Settings s = Settings.defaults().withRest(2345, false);
        store.save(s);
        assertThat(store.load()).isEqualTo(s);
    }

    @Test
    void anUnknownChoiceOfALaterVersionFallsBackForThatFieldOnly() throws Exception {
        Path file = dir.resolve("newer.json");
        Files.writeString(file, "{\"connectionType\":\"BLUETOOTH\",\"tcpHost\":\"timing\",\"tcpPort\":9000,\"serialPort\":\"COM4\",\"serialMode\":\"ARES99\",\"autoSaveSeconds\":30,\"restPort\":2000,\"restEnabled\":true,\"replayFile\":\"\"}");
        Settings loaded = new DefaultSettingsStore(file).load();
        assertThat(loaded.connectionType()).isEqualTo(ConnectionType.TCP);
        assertThat(loaded.serialMode()).isEqualTo(SerialMode.ARES21);
        assertThat(loaded.tcpHost()).isEqualTo("timing");
        assertThat(loaded.tcpPort()).isEqualTo(9000);
        assertThat(loaded.serialPort()).isEqualTo("COM4");
        assertThat(loaded.autoSaveSeconds()).isEqualTo(30);
        assertThat(loaded.restPort()).isEqualTo(2000);
    }

    @Test
    void defaultsFileOfAnEarlierVersionLoadsAsTcp() throws Exception {
        Path file = dir.resolve("old.json");
        Files.writeString(file, "{\"tcpHost\":\"timing\",\"tcpPort\":9000,\"competitionKey\":\"WK\",\"autoSaveSeconds\":30,\"restPort\":0}");
        assertThat(new DefaultSettingsStore(file).load()).isEqualTo(new Settings("timing", 9000, 30, 0).withCompetitionKey("WK"));
    }
}
