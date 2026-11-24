package org.lisasp.alphatimer.test.refinedmessages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeOrigin;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedTimeMessage;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupTimeTest {

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
    void messageType7OnTheLastLapIsABackupFinish() {
        refiner.accept(message(MessageType.BackupTime, KindOfTime.Backup, TimeType.UnknownValue1, TimeInfo.Normal, (byte) 6, (byte) 4, (byte) 4, 151_410));

        assertEquals(List.of(backup(RefinedMessageType.CurrentRaceResult, RefinedKindOfTime.Finish, (byte) 6, (byte) 4, (byte) 4, 151_410, RefinedTimeType.Normal)),
                     backupMessages());
    }

    @Test
    void messageType7BeforeTheLastLapIsABackupSplit() {
        refiner.accept(message(MessageType.BackupTime, KindOfTime.Backup, TimeType.UnknownValue1, TimeInfo.Normal, (byte) 6, (byte) 2, (byte) 4, 70_120));

        assertEquals(List.of(backup(RefinedMessageType.CurrentRaceResult, RefinedKindOfTime.SplitTime, (byte) 6, (byte) 2, (byte) 4, 70_120, RefinedTimeType.Normal)),
                     backupMessages());
    }

    @ParameterizedTest
    @CsvSource({"Finish, Finish", "SplitTime, SplitTime"})
    void aTimeWithTimeInfoBackupIsABackupTimeOfItsKind(KindOfTime kind, RefinedKindOfTime refinedKind) {
        refiner.accept(message(MessageType.CurrentRaceResultsWithBackupTimes, kind, TimeType.Empty, TimeInfo.Backup, (byte) 2, (byte) 1, (byte) 1, 12_100));

        assertEquals(List.of(backup(RefinedMessageType.CurrentRaceResult, refinedKind, (byte) 2, (byte) 1, (byte) 1, 12_100, RefinedTimeType.Normal)),
                     listener.received);
    }

    @ParameterizedTest
    @CsvSource({"OnLineTime, Live", "CurrentRaceResults, CurrentRaceResult", "PreviousRaceResults, PreviousRaceResult",
            "PreviousRaceResultsWithBackupTimes, PreviousRaceResult"})
    void theRefinedMessageTypeFollowsTheMessageType(MessageType type, RefinedMessageType expected) {
        refiner.accept(message(type, KindOfTime.Finish, TimeType.Empty, TimeInfo.Backup, (byte) 2, (byte) 1, (byte) 1, 12_100));

        assertEquals(List.of(backup(expected, RefinedKindOfTime.Finish, (byte) 2, (byte) 1, (byte) 1, 12_100, RefinedTimeType.Normal)), backupMessages());
    }

    @ParameterizedTest
    @CsvSource({"BackupTime, Normal", "CurrentRaceResultsWithBackupTimes, Backup"})
    void aDeletedBackupTimeIsRefinedAsDeletion(MessageType type, TimeInfo timeInfo) {
        KindOfTime kind = type == MessageType.BackupTime ? KindOfTime.Backup : KindOfTime.Finish;

        refiner.accept(message(type, kind, TimeType.DeletedTime, timeInfo, (byte) 2, (byte) 1, (byte) 1, 12_100));

        assertEquals(List.of(backup(RefinedMessageType.CurrentRaceResult, RefinedKindOfTime.Finish, (byte) 2, (byte) 1, (byte) 1, 12_100, RefinedTimeType.DeletedTime)),
                     backupMessages());
    }

    @Test
    void aBackupTimeWithoutLaneIsDropped() {
        DataHandlingMessage message = message(MessageType.BackupTime, KindOfTime.Backup, TimeType.UnknownValue1, TimeInfo.Normal, (byte) 0, (byte) 1, (byte) 1, 13_710);

        refiner.accept(message);

        assertEquals(List.of(new DroppedTimeMessage(message)), backupMessages());
    }

    @ParameterizedTest
    @CsvSource({"0, 12100", "1, 0"})
    void aBackupTimeWithoutLapOrTimeIsDropped(byte lap, int time) {
        DataHandlingMessage message = message(MessageType.BackupTime, KindOfTime.Backup, TimeType.UnknownValue1, TimeInfo.Normal, (byte) 2, lap, (byte) 1, time);

        refiner.accept(message);

        assertEquals(List.of(new DroppedTimeMessage(message)), backupMessages());
    }

    @ParameterizedTest
    @CsvSource({"DidNotStart", "DidNotFinish", "Disqualified", "Plus"})
    void aBackupTimeWithATimeMarkerIsDropped(TimeMarker marker) {
        DataHandlingMessage message = message(MessageType.BackupTime, KindOfTime.Backup, TimeType.UnknownValue1, TimeInfo.Normal, (byte) 2, (byte) 1, (byte) 1, 12_100,
                                              marker);

        refiner.accept(message);

        assertEquals(List.of(new DroppedTimeMessage(message)), backupMessages());
    }

    @ParameterizedTest
    @CsvSource({"Empty", "Finish", "SplitTime"})
    void messageType7WithAnotherKindOfTimeIsNotABackupTime(KindOfTime kind) {
        refiner.accept(message(MessageType.BackupTime, kind, TimeType.UnknownValue1, TimeInfo.Normal, (byte) 2, (byte) 1, (byte) 1, 12_100));

        assertTrue(backupMessages().isEmpty());
    }

    @Test
    void anOrdinaryResultTimeIsAPrimaryTime() {
        refiner.accept(message(MessageType.CurrentRaceResultsWithBackupTimes, KindOfTime.Finish, TimeType.Empty, TimeInfo.Normal, (byte) 2, (byte) 1, (byte) 1, 12_100));

        assertTrue(backupMessages().isEmpty());
        assertTrue(listener.received.stream().anyMatch(m -> m instanceof TimeMessage t && t.getOrigin() == RefinedTimeOrigin.Primary));
    }

    private List<RefinedMessage> backupMessages() {
        return listener.received.stream()
                               .filter(m -> (m instanceof TimeMessage t && t.getOrigin() == RefinedTimeOrigin.Backup) || m instanceof DroppedTimeMessage)
                               .toList();
    }

    private static TimeMessage backup(RefinedMessageType type, RefinedKindOfTime kind, byte lane, byte lap, byte lapCount, int time, RefinedTimeType timeType) {
        return new TimeMessage(TIMESTAMP, "TestWK", (short) 3, (byte) 4, type, kind, lane, lap, lapCount, (byte) 0, time, timeType, RefinedTimeOrigin.Backup);
    }

    private static DataHandlingMessage message(MessageType type, KindOfTime kind, TimeType timeType, TimeInfo timeInfo, byte lane, byte lap, byte lapCount, int time) {
        return message(type, kind, timeType, timeInfo, lane, lap, lapCount, time, TimeMarker.Empty);
    }

    private static DataHandlingMessage message(MessageType type, KindOfTime kind, TimeType timeType, TimeInfo timeInfo, byte lane, byte lap, byte lapCount, int time,
                                               TimeMarker marker) {
        return new DataHandlingMessage(
                TIMESTAMP,
                "TestWK",
                "1", "2",
                type,
                kind,
                timeType,
                new UsedLanes(new boolean[]{true, true, false, false, false, false, false, false, false, false}),
                lapCount,
                (short) 3,
                (byte) 4,
                (byte) 0,
                RankInfo.Normal,
                lane,
                lap,
                time,
                timeInfo,
                marker);
    }
}
