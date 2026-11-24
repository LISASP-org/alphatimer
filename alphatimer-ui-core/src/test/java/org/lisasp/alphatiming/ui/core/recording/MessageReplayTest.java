package org.lisasp.alphatiming.ui.core.recording;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.RankInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.alphatiming.project.db.testdata.DataHandlingMessageTestData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class MessageReplayTest {

    private static DataHandlingMessage startOf(int heat) {
        return new DataHandlingMessage(LocalDateTime.of(2021, 6, 21, 14, 53), "", "a", "b", MessageType.OnLineTime, KindOfTime.Start, TimeType.Empty,
                                       DataHandlingMessageTestData.createUsedLanes(), (byte) 2, (short) 1, (byte) heat, (byte) 0, RankInfo.Normal, (byte) 0,
                                       (byte) 0, 0, TimeInfo.Normal, TimeMarker.Empty);
    }

    @Test
    void aMessageTheLegacySideRejectsStillReachesTheHeats() {
        List<Integer> legacy = new ArrayList<>();
        Set<Integer> heats = new TreeSet<>();

        Set<Integer> refinedLegacy = new TreeSet<>();

        MessageReplay.replay(List.of(startOf(1), startOf(2)), m -> {
            legacy.add((int) m.getHeat());
            throw new IllegalStateException("rejected");
        }, m -> refinedLegacy.add((int) m.getHeat()), m -> heats.add((int) m.getHeat()));

        assertThat(legacy).containsExactly(1, 2);
        assertThat(refinedLegacy).containsExactly(1, 2);
        assertThat(heats).containsExactly(1, 2);
    }

    @Test
    void aMessageTheHeatsRejectDoesNotKeepTheFollowingMessagesFromBothSides() {
        List<Integer> legacy = new ArrayList<>();
        Set<Integer> heats = new TreeSet<>();

        Set<Integer> refinedLegacy = new TreeSet<>();

        MessageReplay.replay(List.of(startOf(1), startOf(2)), m -> legacy.add((int) m.getHeat()), m -> refinedLegacy.add((int) m.getHeat()), m -> {
            heats.add((int) m.getHeat());
            throw new IllegalStateException("rejected");
        });

        assertThat(legacy).containsExactly(1, 2);
        assertThat(refinedLegacy).containsExactly(1, 2);
        assertThat(heats).containsExactly(1, 2);
    }

    @Test
    void aMessageTheRefinedLegacySideRejectsStillReachesTheHeats() {
        List<Integer> legacy = new ArrayList<>();
        Set<Integer> refinedLegacy = new TreeSet<>();
        Set<Integer> heats = new TreeSet<>();

        MessageReplay.replay(List.of(startOf(1), startOf(2)), m -> legacy.add((int) m.getHeat()), m -> {
            refinedLegacy.add((int) m.getHeat());
            throw new IllegalStateException("rejected");
        }, m -> heats.add((int) m.getHeat()));

        assertThat(legacy).containsExactly(1, 2);
        assertThat(refinedLegacy).containsExactly(1, 2);
        assertThat(heats).containsExactly(1, 2);
    }
}
