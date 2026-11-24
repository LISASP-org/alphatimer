package org.lisasp.alphatiming.ui.core.settings;

import org.lisasp.alphatimer.serial.com.SerialPorts;

import java.util.ArrayList;
import java.util.List;

/**
 * @param competitionKey          one value per project file; it names all data of the project, so a change renames them all
 * @param timekeepingOffsetHours  added to the times of day the device sent whenever they are shown; what is recorded keeps
 *                                the transmitted value, so a change also applies to what was recorded before
 * @param lenexIntervalSeconds    how often the LENEX file is checked; one of {@link #LENEX_INTERVALS_SECONDS}
 */
public record Settings(ConnectionType connectionType,
                       String tcpHost,
                       int tcpPort,
                       String serialPort,
                       SerialMode serialMode,
                       int autoSaveSeconds,
                       int restPort,
                       boolean restEnabled,
                       String replayFile,
                       String competitionKey,
                       int timekeepingOffsetHours,
                       String lenexFile,
                       int lenexIntervalSeconds) {

    public static final int DEFAULT_REST_PORT = 1999;
    public static final int MAX_TIMEKEEPING_OFFSET_HOURS = 23;
    public static final List<Integer> LENEX_INTERVALS_SECONDS = List.of(1, 5, 15, 30, 60);
    public static final int DEFAULT_LENEX_INTERVAL_SECONDS = 1;

    public Settings {
        connectionType = connectionType == null ? ConnectionType.TCP : connectionType;
        serialMode = serialMode == null ? SerialMode.ARES21 : serialMode;
        tcpHost = tcpHost == null ? "" : tcpHost;
        serialPort = serialPort == null ? "" : serialPort;
        replayFile = replayFile == null ? "" : replayFile;
        competitionKey = competitionKey == null ? "" : competitionKey;
        lenexFile = lenexFile == null ? "" : lenexFile;
        lenexIntervalSeconds = lenexIntervalSeconds == 0 ? DEFAULT_LENEX_INTERVAL_SECONDS : lenexIntervalSeconds;
    }

    public Settings(ConnectionType connectionType, String tcpHost, int tcpPort, String serialPort, SerialMode serialMode, int autoSaveSeconds, int restPort, boolean restEnabled, String replayFile, String competitionKey, int timekeepingOffsetHours) {
        this(connectionType, tcpHost, tcpPort, serialPort, serialMode, autoSaveSeconds, restPort, restEnabled, replayFile, competitionKey, timekeepingOffsetHours, "", DEFAULT_LENEX_INTERVAL_SECONDS);
    }

    public Settings(ConnectionType connectionType, String tcpHost, int tcpPort, String serialPort, SerialMode serialMode, int autoSaveSeconds, int restPort, boolean restEnabled, String replayFile, String competitionKey) {
        this(connectionType, tcpHost, tcpPort, serialPort, serialMode, autoSaveSeconds, restPort, restEnabled, replayFile, competitionKey, 0);
    }

    public Settings(ConnectionType connectionType, String tcpHost, int tcpPort, String serialPort, SerialMode serialMode, int autoSaveSeconds, int restPort, boolean restEnabled, String replayFile) {
        this(connectionType, tcpHost, tcpPort, serialPort, serialMode, autoSaveSeconds, restPort, restEnabled, replayFile, "");
    }

    public Settings(ConnectionType connectionType, String tcpHost, int tcpPort, String serialPort, SerialMode serialMode, int autoSaveSeconds, int restPort, boolean restEnabled) {
        this(connectionType, tcpHost, tcpPort, serialPort, serialMode, autoSaveSeconds, restPort, restEnabled, "");
    }

    public Settings(String tcpHost, int tcpPort, int autoSaveSeconds, int restPort) {
        this(ConnectionType.TCP, tcpHost, tcpPort, "", SerialMode.ARES21, autoSaveSeconds, restPort == 0 ? DEFAULT_REST_PORT : restPort, restPort != 0);
    }

    public static Settings defaults() {
        return new Settings("", 8585, 60, DEFAULT_REST_PORT);
    }

    public Settings withTcp(String host, int port) {
        return new Settings(connectionType, host, port, serialPort, serialMode, autoSaveSeconds, restPort, restEnabled, replayFile, competitionKey, timekeepingOffsetHours, lenexFile, lenexIntervalSeconds);
    }

    public Settings withSerial(String port, SerialMode mode) {
        return new Settings(connectionType, tcpHost, tcpPort, port, mode, autoSaveSeconds, restPort, restEnabled, replayFile, competitionKey, timekeepingOffsetHours, lenexFile, lenexIntervalSeconds);
    }

    public Settings withConnectionType(ConnectionType type) {
        return new Settings(type, tcpHost, tcpPort, serialPort, serialMode, autoSaveSeconds, restPort, restEnabled, replayFile, competitionKey, timekeepingOffsetHours, lenexFile, lenexIntervalSeconds);
    }

    public Settings withReplayFile(String file) {
        return new Settings(connectionType, tcpHost, tcpPort, serialPort, serialMode, autoSaveSeconds, restPort, restEnabled, file, competitionKey, timekeepingOffsetHours, lenexFile, lenexIntervalSeconds);
    }

    public Settings withRest(int port, boolean enabled) {
        return new Settings(connectionType, tcpHost, tcpPort, serialPort, serialMode, autoSaveSeconds, port, enabled, replayFile, competitionKey, timekeepingOffsetHours, lenexFile, lenexIntervalSeconds);
    }

    public Settings withCompetitionKey(String key) {
        return new Settings(connectionType, tcpHost, tcpPort, serialPort, serialMode, autoSaveSeconds, restPort, restEnabled, replayFile, key, timekeepingOffsetHours, lenexFile, lenexIntervalSeconds);
    }

    public Settings withTimekeepingOffset(int hours) {
        return new Settings(connectionType, tcpHost, tcpPort, serialPort, serialMode, autoSaveSeconds, restPort, restEnabled, replayFile, competitionKey, hours, lenexFile, lenexIntervalSeconds);
    }

    public Settings withLenex(String file, int intervalSeconds) {
        return new Settings(connectionType, tcpHost, tcpPort, serialPort, serialMode, autoSaveSeconds, restPort, restEnabled, replayFile, competitionKey, timekeepingOffsetHours, file, intervalSeconds);
    }

    public List<SettingsProblem> validate() {
        List<SettingsProblem> problems = new ArrayList<>();
        switch (connectionType) {
            case TCP -> {
                if (tcpHost.isBlank()) {
                    problems.add(SettingsProblem.TCP_HOST_EMPTY);
                }
                if (tcpPort < 1 || tcpPort > 65535) {
                    problems.add(SettingsProblem.TCP_PORT_RANGE);
                }
            }
            case SERIAL -> {
                if (serialPort.isBlank()) {
                    problems.add(SettingsProblem.SERIAL_PORT_MISSING);
                } else if (!SerialPorts.fitsDriver(serialPort)) {
                    problems.add(SettingsProblem.SERIAL_PORT_NAME_TOO_LONG);
                }
            }
            case FILE -> {
                if (replayFile.isBlank()) {
                    problems.add(SettingsProblem.REPLAY_FILE_MISSING);
                }
            }
            case LENEX -> {
                if (lenexFile.isBlank()) {
                    problems.add(SettingsProblem.LENEX_FILE_MISSING);
                }
                if (!LENEX_INTERVALS_SECONDS.contains(lenexIntervalSeconds)) {
                    problems.add(SettingsProblem.LENEX_INTERVAL_INVALID);
                }
            }
        }
        if (competitionKey.isBlank()) {
            problems.add(SettingsProblem.COMPETITION_KEY_EMPTY);
        }
        if (Math.abs(timekeepingOffsetHours) > MAX_TIMEKEEPING_OFFSET_HOURS) {
            problems.add(SettingsProblem.TIMEKEEPING_OFFSET_RANGE);
        }
        if (autoSaveSeconds < 5 || autoSaveSeconds > 3600) {
            problems.add(SettingsProblem.AUTO_SAVE_RANGE);
        }
        if (restEnabled && (restPort < 1 || restPort > 65535)) {
            problems.add(SettingsProblem.REST_PORT_RANGE);
        }
        return problems;
    }
}
