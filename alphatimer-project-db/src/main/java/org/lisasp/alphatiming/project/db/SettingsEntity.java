package org.lisasp.alphatiming.project.db;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "SettingsEntity")
@Table(name = "settings")
@Getter
@Setter
@NoArgsConstructor
public class SettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tcpHost;
    private int tcpPort;
    /** Stored since the first version, ignored for a while: null in files written then. */
    private String competitionKey;
    private int autoSaveSeconds;
    private int restPort;
    /** Added after the first version: null in older project files, which means TCP / ARES21. */
    private String connectionType;
    private String serialPort;
    private String serialMode;
    /** Added after the first version: null in older project files, which mean "enabled unless the port is 0". */
    private Boolean restEnabled;
    /** Added after the first version: null in older project files. */
    private String replayFile;
    /** Added after the first version: null in older project files, which means no offset. */
    private Integer timekeepingOffsetHours;

    public SettingsEntity(String tcpHost, int tcpPort, int autoSaveSeconds, int restPort) {
        this.tcpHost = tcpHost;
        this.tcpPort = tcpPort;
        this.autoSaveSeconds = autoSaveSeconds;
        this.restPort = restPort;
    }
}
