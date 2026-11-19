package org.lisasp.alphatimer.test.refinedmessages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.BackupTimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedBackupTimeMessage;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupTimeMessageTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2021, 6, 1, 10, 0);

    private DataHandlingMessageRefiner refiner;
    private TestRefinedMessageListener listener;

    @BeforeEach
    void prepare() {
        listener = new TestRefinedMessageListener();
        refiner = new DataHandlingMessageRefiner();
        refiner.register(listener);
    }

    @Test
    void messageType7IsABackupTime() {
        refiner.accept(message(MessageType.BackupTime, KindOfTime.Backup, TimeType.UnknownValue1, TimeInfo.Normal, (byte) 6, (byte) 1, 13710));

        assertEquals(List.of(new BackupTimeMessage(TIMESTAMP, "TestWK", (short) 3, (byte) 4, RefinedMessageType.CurrentRaceResult, (byte) 6, (byte) 1, 13710)),
                     backupMessages());
    }

    @Test
    void aResultTimeWithTimeInfoBackupIsABackupTime() {
        refiner.accept(message(MessageType.CurrentRaceResultsWithBackupTimes, KindOfTime.Finish, TimeType.Empty, TimeInfo.Backup, (byte) 2, (byte) 1, 12100));

        assertEquals(List.of(new BackupTimeMessage(TIMESTAMP, "TestWK", (short) 3, (byte) 4, RefinedMessageType.CurrentRaceResult, (byte) 2, (byte) 1, 12100)),
                     backupMessages());
        assertTrue(listener.received.stream().noneMatch(m -> m instanceof TimeMessage));
    }

    @Test
    void aBackupTimeWithoutLaneIsDropped() {
        DataHandlingMessage message = message(MessageType.BackupTime, KindOfTime.Backup, TimeType.UnknownValue1, TimeInfo.Normal, (byte) 0, (byte) 1, 13710);

        refiner.accept(message);

        assertEquals(List.of(new DroppedBackupTimeMessage(message)), backupMessages());
    }

    private List<RefinedMessage> backupMessages() {
        return listener.received.stream()
                               .filter(m -> m instanceof BackupTimeMessage || m instanceof DroppedBackupTimeMessage)
                               .toList();
    }

    private static DataHandlingMessage message(MessageType type, KindOfTime kind, TimeType timeType, TimeInfo timeInfo, byte lane, byte lap, int time) {
        return new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                type,
                kind,
                timeType,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                (byte) 1,
                (short) 3,
                (byte) 4,
                (byte) 0,
                RankInfo.Normal,
                lane,
                lap,
                time,
                timeInfo,
                TimeMarker.Empty);
    }
}
