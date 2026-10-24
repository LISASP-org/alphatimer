package org.lisasp.alphatiming.ui.core.settings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsTest {

    private static Settings valid() {
        return new Settings("localhost", 8585, 60, 1999).withCompetitionKey("WK");
    }

    @Test
    void validSettingsHaveNoProblems() {
        assertThat(valid().validate()).isEmpty();
        assertThat(new Settings("h", 1, 5, 0).withCompetitionKey("WK").validate()).isEmpty();
        assertThat(new Settings("h", 65535, 3600, 65535).withCompetitionKey("WK").validate()).isEmpty();
    }

    @Test
    void blankHostIsInvalid() {
        assertThat(new Settings(" ", 8585, 60, 0).withCompetitionKey("WK").validate()).containsExactly(SettingsProblem.TCP_HOST_EMPTY);
        assertThat(new Settings(null, 8585, 60, 0).withCompetitionKey("WK").validate()).containsExactly(SettingsProblem.TCP_HOST_EMPTY);
    }

    @Test
    void portsMustBeInRange() {
        assertThat(new Settings("h", 0, 60, 0).withCompetitionKey("WK").validate()).containsExactly(SettingsProblem.TCP_PORT_RANGE);
        assertThat(new Settings("h", 70000, 60, 0).withCompetitionKey("WK").validate()).hasSize(1);
        assertThat(new Settings("h", 1, 60, -1).withCompetitionKey("WK").validate()).containsExactly(SettingsProblem.REST_PORT_RANGE);
        assertThat(new Settings("h", 1, 60, 70000).withCompetitionKey("WK").validate()).hasSize(1);
    }

    @Test
    void autoSaveIntervalMustBeInRange() {
        assertThat(new Settings("h", 1, 4, 0).withCompetitionKey("WK").validate()).containsExactly(SettingsProblem.AUTO_SAVE_RANGE);
        assertThat(new Settings("h", 1, 3601, 0).withCompetitionKey("WK").validate()).hasSize(1);
    }

    @Test
    void defaultsNeedAHostAndACompetitionKeyBeforeTheyAreValid() {
        assertThat(Settings.defaults().validate()).containsExactly(SettingsProblem.TCP_HOST_EMPTY, SettingsProblem.COMPETITION_KEY_EMPTY);
        assertThat(Settings.defaults().withTcp("h", 9).tcpPort()).isEqualTo(9);
    }

    @Test
    void serialConnectionNeedsAPortButNoTcpHost() {
        Settings serial = new Settings(ConnectionType.SERIAL, "", 0, "", SerialMode.ARES21, 60, 1999, false).withCompetitionKey("WK");
        assertThat(serial.validate()).containsExactly(SettingsProblem.SERIAL_PORT_MISSING);
        assertThat(new Settings(ConnectionType.SERIAL, "", 0, "/dev/ttyUSB0", SerialMode.QUANTUM, 60, 1999, false).withCompetitionKey("WK").validate()).isEmpty();
    }

    @Test
    void fileConnectionNeedsAFileButNeitherHostNorPort() {
        Settings file = new Settings("h", 1, 60, 0).withCompetitionKey("WK").withConnectionType(ConnectionType.FILE).withTcp("", 0);
        assertThat(file.validate()).containsExactly(SettingsProblem.REPLAY_FILE_MISSING);
        assertThat(file.withReplayFile("/data/run.serial").validate()).isEmpty();
        assertThat(new Settings("h", 1, 60, 0).withCompetitionKey("WK").withReplayFile("/data/run.serial").validate()).isEmpty();
    }

    @Test
    void tcpConnectionIgnoresTheSerialPort() {
        assertThat(new Settings(ConnectionType.TCP, "h", 1, "", SerialMode.ARES21, 60, 1999, false).withCompetitionKey("WK").validate()).isEmpty();
    }

    @Test
    void missingValuesFallBackToDefaults() {
        Settings s = new Settings(null, null, 8585, null, null, 60, 1999, false).withCompetitionKey("WK");
        assertThat(s.connectionType()).isEqualTo(ConnectionType.TCP);
        assertThat(s.serialMode()).isEqualTo(SerialMode.ARES21);
        assertThat(s.tcpHost()).isEmpty();
        assertThat(s.serialPort()).isEmpty();
    }

    @Test
    void convenienceConstructorMeansTcpAndZeroMeansNoServer() {
        assertThat(new Settings("h", 1, 60, 0).withCompetitionKey("WK").connectionType()).isEqualTo(ConnectionType.TCP);
        assertThat(new Settings("h", 1, 60, 0).withCompetitionKey("WK").restEnabled()).isFalse();
        assertThat(new Settings("h", 1, 60, 2000).withCompetitionKey("WK").restEnabled()).isTrue();
        assertThat(new Settings("h", 1, 60, 2000).withCompetitionKey("WK").restPort()).isEqualTo(2000);
    }

    @Test
    void theServerPortOnlyMattersWhileTheServerIsEnabled() {
        Settings enabled = new Settings(ConnectionType.TCP, "h", 1, "", SerialMode.ARES21, 60, 70000, true).withCompetitionKey("WK");
        assertThat(enabled.validate()).containsExactly(SettingsProblem.REST_PORT_RANGE);
        assertThat(enabled.withRest(0, true).validate()).containsExactly(SettingsProblem.REST_PORT_RANGE);
        assertThat(enabled.withRest(70000, false).validate()).isEmpty();
        assertThat(enabled.withRest(1999, true).validate()).isEmpty();
    }

    @Test
    void theCompetitionKeyIsRequiredAndKeptByEveryChange() {
        assertThat(new Settings("h", 1, 60, 0).validate()).containsExactly(SettingsProblem.COMPETITION_KEY_EMPTY);
        Settings keyed = new Settings("h", 1, 60, 0).withCompetitionKey("WK");
        assertThat(keyed.validate()).isEmpty();
        assertThat(keyed.withTcp("x", 2).withSerial("COM1", SerialMode.QUANTUM).withConnectionType(ConnectionType.SERIAL).withReplayFile("f").withRest(5, true).competitionKey()).isEqualTo("WK");
        assertThat(keyed.withCompetitionKey(null).competitionKey()).isEmpty();
    }
}
