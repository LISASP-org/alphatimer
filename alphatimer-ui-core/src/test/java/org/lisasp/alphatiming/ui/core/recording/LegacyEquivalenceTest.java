package org.lisasp.alphatiming.ui.core.recording;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.RankInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.alphatiming.legacy.api.LegacySerialization;
import org.lisasp.alphatiming.legacy.server.InMemoryLaneTimeRepository;
import org.lisasp.alphatiming.legacy.server.LegacyService;
import org.lisasp.alphatiming.legacy.server.refined.RefinedLegacyService;
import org.lisasp.alphatiming.project.db.testdata.DataHandlingMessageTestData;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyEquivalenceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2021, 6, 21, 14, 53);

    private static DataHandlingMessage time(int second, MessageType type, KindOfTime kind, TimeType timeType, int event, int heat, int lane, int lap,
                                            int millis) {
        return new DataHandlingMessage(T0.plusSeconds(second), "", "a", "b", type, kind, timeType, DataHandlingMessageTestData.createUsedLanes(),
                                       (byte) 2, (short) event, (byte) heat, (byte) 0, RankInfo.Normal, (byte) lane, (byte) lap, millis,
                                       TimeInfo.Normal, TimeMarker.Empty);
    }

    private static DataHandlingMessage finish(int second, MessageType type, int event, int heat, int lane, int millis) {
        return time(second, type, KindOfTime.Finish, TimeType.Empty, event, heat, lane, 2, millis);
    }

    private static DataHandlingMessage deleted(int second, int event, int heat, int lane, int lap, int millis) {
        return time(second, MessageType.OnLineTime, KindOfTime.SplitTime, TimeType.DeletedTime, event, heat, lane, lap, millis);
    }

    @Test
    void bothInstancesServeTheSameHeatsForAresMessages() {
        List<DataHandlingMessage> messages = List.of(
                time(0, MessageType.OnLineTime, KindOfTime.Start, TimeType.Empty, 1, 1, 0, 0, 0),
                time(10, MessageType.OnLineTime, KindOfTime.SplitTime, TimeType.Empty, 1, 1, 1, 1, 15_000),
                finish(20, MessageType.OnLineTime, 1, 1, 1, 31_000),
                finish(21, MessageType.OnLineTime, 1, 1, 2, 32_000),
                finish(22, MessageType.OnLineTime, 1, 1, 3, 33_000),
                deleted(23, 1, 1, 2, 2, 32_000),
                deleted(24, 1, 1, 3, 1, 33_000),
                finish(25, MessageType.OnLineTime, 1, 1, 2, 32_500),
                finish(26, MessageType.OnLineTime, 1, 1, 2, 32_700),
                finish(30, MessageType.CurrentRaceResults, 1, 1, 1, 31_000),
                finish(31, MessageType.OnLineTime, 0, 1, 1, 9_000),
                finish(32, MessageType.OnLineTime, 1, 0, 1, 9_000),
                time(33, MessageType.OfficialEnd, KindOfTime.Empty, TimeType.Empty, 1, 1, 0, 0, 0),
                finish(40, MessageType.OnLineTime, 2, 1, 4, 61_000),
                finish(50, MessageType.PreviousRaceResults, 1, 1, 1, 31_000));
        LegacyService legacy = new LegacyService(new InMemoryLaneTimeRepository());
        RefinedLegacyService refined = new RefinedLegacyService(new InMemoryLaneTimeRepository());

        MessageReplay.replay(messages, legacy, refined, m -> {
        });

        assertThat(legacy.getHeats()).hasSize(2);
        assertThat(LegacySerialization.toXML(refined.getHeats())).isEqualTo(LegacySerialization.toXML(legacy.getHeats()));
    }
}
