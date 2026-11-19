package org.lisasp.alphatimer.api.ares.serial.events.messages.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum MessageType {
    ReadyToStart((byte)0),
    OfficialEnd((byte)1),
    OnLineTime((byte)2),
    CurrentRaceResults((byte)3),
    CurrentRaceResultsWithBackupTimes((byte)4),
    PreviousRaceResults((byte)5),
    PreviousRaceResultsWithBackupTimes((byte)6),
    BackupTime((byte)7);

    private final byte value;
}
