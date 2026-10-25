package org.lisasp.alphatiming.project.db;

import org.lisasp.alphatiming.legacy.api.LegacySerialization;
import org.lisasp.alphatiming.legacy.server.LegacyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.RankInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.alphatiming.legacy.api.Heat;
import org.lisasp.alphatiming.project.db.testdata.DataHandlingMessageTestData;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyServiceTest {

    @TempDir
    Path dir;

    private static DataHandlingMessage msg(String competition, int event, int heat, int lane, KindOfTime kind, MessageType type, int millis) {
        return new DataHandlingMessage(LocalDateTime.of(2021, 6, 21, 14, 53),
                                       competition,
                                       "a",
                                       "b",
                                       type,
                                       kind,
                                       TimeType.Empty,
                                       DataHandlingMessageTestData.createUsedLanes(),
                                       (byte) 2,
                                       (short) event,
                                       (byte) heat,
                                       (byte) 0,
                                       RankInfo.Normal,
                                       (byte) lane,
                                       (byte) 0,
                                       millis,
                                       TimeInfo.Normal,
                                       TimeMarker.Empty);
    }

    private static DataHandlingMessage deleted(int event, int heat, int lane, int lap, int millis) {
        return new DataHandlingMessage(LocalDateTime.of(2021, 6, 21, 14, 54),
                                       "WK",
                                       "a",
                                       "b",
                                       MessageType.OnLineTime,
                                       KindOfTime.SplitTime,
                                       TimeType.DeletedTime,
                                       DataHandlingMessageTestData.createUsedLanes(),
                                       (byte) 2,
                                       (short) event,
                                       (byte) heat,
                                       (byte) 0,
                                       RankInfo.Normal,
                                       (byte) lane,
                                       (byte) lap,
                                       millis,
                                       TimeInfo.Normal,
                                       TimeMarker.Empty);
    }

    private static long[] times(LegacyService service, int laneIndex) {
        return service.getHeats()[0].getLanes().get(laneIndex).getTimes();
    }

    @Test
    void aDeletedFinishIsWithdrawnUntilTheNextFinish() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            LegacyService service = new LegacyService(new LaneTimeStore(db));
            service.accept(msg("WK", 1, 1, 2, KindOfTime.Finish, MessageType.OnLineTime, 25_000));
            service.accept(msg("WK", 1, 1, 3, KindOfTime.Finish, MessageType.OnLineTime, 26_000));

            service.accept(deleted(1, 1, 2, 2, 25_000));

            assertThat(times(service, 1)).isEmpty();
            assertThat(times(service, 2)).containsExactly(26_000);

            service.accept(msg("WK", 1, 1, 2, KindOfTime.Finish, MessageType.OnLineTime, 27_000));
            assertThat(times(service, 1)).containsExactly(27_000);
            db.closeAndDelete();
        }
    }

    @Test
    void aDeletedFinishKeepsTheEarlierFinishesOfTheLane() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            LegacyService service = new LegacyService(new LaneTimeStore(db));
            service.accept(msg("WK", 1, 1, 2, KindOfTime.Finish, MessageType.OnLineTime, 24_000));
            service.accept(msg("WK", 1, 1, 2, KindOfTime.Finish, MessageType.OnLineTime, 25_000));

            service.accept(deleted(1, 1, 2, 2, 25_000));

            assertThat(times(service, 1)).containsExactly(24_000);
            db.closeAndDelete();
        }
    }

    @Test
    void aDeletedTimeOtherThanTheFinishKeepsTheFinish() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            LegacyService service = new LegacyService(new LaneTimeStore(db));
            service.accept(msg("WK", 1, 1, 2, KindOfTime.Finish, MessageType.OnLineTime, 25_000));

            service.accept(deleted(1, 1, 2, 1, 25_000));
            service.accept(deleted(1, 1, 2, 2, 24_000));
            service.accept(deleted(1, 2, 2, 2, 25_000));

            assertThat(service.getHeats()).hasSize(1);
            assertThat(times(service, 1)).containsExactly(25_000);
            db.closeAndDelete();
        }
    }

    @Test
    void storesFinishTimesPerHeatAndIgnoresEverythingElse() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            LegacyService service = new LegacyService(new LaneTimeStore(db));
            assertThat(service.getHeats()).isEmpty();

            service.accept(msg("WK", 1, 1, 2, KindOfTime.Finish, MessageType.OnLineTime, 19_000));
            service.accept(msg("WK", 1, 1, 3, KindOfTime.Finish, MessageType.CurrentRaceResults, 19_500));
            service.accept(msg("WK", 1, 2, 1, KindOfTime.Finish, MessageType.OnLineTime, 20_000));
            service.accept(msg("WK", 1, 1, 4, KindOfTime.SplitTime, MessageType.OnLineTime, 9_000)); // not a finish
            service.accept(msg("WK", 0, 1, 4, KindOfTime.Finish, MessageType.OnLineTime, 9_000)); // no event
            service.accept(msg("WK", 1, 0, 4, KindOfTime.Finish, MessageType.OnLineTime, 9_000)); // no heat
            service.accept(msg("WK", 1, 1, 4, KindOfTime.Finish, MessageType.OfficialEnd, 9_000)); // official end

            Heat[] heats = service.getHeats();
            assertThat(heats).hasSize(2);
            assertThat(heats[0].getEvent()).isEqualTo(1);
            assertThat(heats[0].getHeat()).isEqualTo(1);
            assertThat(heats[0].getLanes()).containsOnlyKeys(0, 1, 2);
            assertThat(heats[1].getHeat()).isEqualTo(2);
            assertThat(LegacySerialization.toXML(heats)).contains("<AlphaServer.Heat event=\"1\" heat=\"1\"").contains("<long>19000</long>");
            db.closeAndDelete();
        }
    }
}
