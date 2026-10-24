package org.lisasp.alphatimer.refinedmessages;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.RankInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.StartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TakeOverTimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTimeTest {

    private static DataHandlingMessage message(MessageType type, KindOfTime kind, TimeType timeType, TimeMarker marker, int lap, int lapCount, int lane, int millis) {
        return new DataHandlingMessage(LocalDateTime.of(2026, 1, 1, 10, 0), "", "raw1", "raw2", type, kind, timeType, new UsedLanes(),
                                       (byte) lapCount, (short) 3, (byte) 2, (byte) 0, RankInfo.Normal, (byte) lane, (byte) lap, millis, TimeInfo.Normal, marker);
    }

    private static List<RefinedMessage> refine(DataHandlingMessage message) {
        List<RefinedMessage> result = new ArrayList<>();
        DataHandlingMessageRefiner refiner = new DataHandlingMessageRefiner();
        refiner.register(result::add);
        refiner.accept(message);
        return result.stream().filter(m -> !(m instanceof UsedLanesMessage)).toList();
    }

    @Test
    void aStartCarriesTheTimeOfDayOfTheDevice() {
        List<RefinedMessage> refined = refine(message(MessageType.OnLineTime, KindOfTime.Start, TimeType.Empty, TimeMarker.Empty, 0, 2, 0, 112_853_930));
        assertThat(refined).singleElement().isInstanceOf(StartMessage.class);
        assertThat(refined.get(0).getDeviceTimeInMillis()).isEqualTo(OptionalInt.of(112_853_930));
    }

    @Test
    void splitAndFinishTimesCarryTheRaceTimeOfTheDevice() {
        List<RefinedMessage> finish = refine(message(MessageType.OnLineTime, KindOfTime.Finish, TimeType.Empty, TimeMarker.Empty, 2, 2, 2, 25_010));
        assertThat(finish).singleElement().isInstanceOf(TimeMessage.class);
        assertThat(finish.get(0).getDeviceTimeInMillis()).isEqualTo(OptionalInt.of(25_010));

        List<RefinedMessage> split = refine(message(MessageType.OnLineTime, KindOfTime.SplitTime, TimeType.Empty, TimeMarker.Empty, 1, 2, 2, 12_340));
        assertThat(split.get(0).getDeviceTimeInMillis()).isEqualTo(OptionalInt.of(12_340));
    }

    @Test
    void aTimeMessageWithoutATimeHasNoDeviceTime() {
        List<RefinedMessage> deleted = refine(message(MessageType.OnLineTime, KindOfTime.Finish, TimeType.DeletedTime, TimeMarker.Empty, 2, 2, 2, 0));
        assertThat(deleted).singleElement().isInstanceOf(TimeMessage.class);
        assertThat(deleted.get(0).getDeviceTimeInMillis()).isEmpty();
    }

    @Test
    void aTakeOverCarriesItsTimeWhenThereIsOne() {
        List<RefinedMessage> withTime = refine(message(MessageType.OnLineTime, KindOfTime.TakeOverTime, TimeType.PlatformTimeAfterTouchpadTime, TimeMarker.Plus, 1, 3, 2, 1_500));
        assertThat(withTime).singleElement().isInstanceOf(TakeOverTimeMessage.class);
        assertThat(withTime.get(0).getDeviceTimeInMillis()).isEqualTo(OptionalInt.of(1_500));

        List<RefinedMessage> withoutTime = refine(message(MessageType.OnLineTime, KindOfTime.TakeOverTime, TimeType.PlatformTimeAfterTouchpadTime, TimeMarker.Plus, 1, 3, 2, 0));
        assertThat(withoutTime.get(0).getDeviceTimeInMillis()).isEmpty();
    }

    @Test
    void messagesWithoutATimeFieldHaveNoDeviceTime() {
        assertThat(refine(message(MessageType.ReadyToStart, KindOfTime.Empty, TimeType.Empty, TimeMarker.Empty, 0, 2, 0, 0)))
                .singleElement().satisfies(m -> assertThat(m.getDeviceTimeInMillis()).isEmpty());
        assertThat(refine(message(MessageType.OfficialEnd, KindOfTime.Empty, TimeType.Empty, TimeMarker.Empty, 0, 2, 0, 0)))
                .singleElement().satisfies(m -> assertThat(m.getDeviceTimeInMillis()).isEmpty());
        assertThat(refine(message(MessageType.CurrentRaceResults, KindOfTime.Empty, TimeType.Empty, TimeMarker.DidNotStart, 0, 0, 3, 0)))
                .singleElement().satisfies(m -> assertThat(m.getDeviceTimeInMillis()).isEmpty());
    }

    @Test
    void droppedMessagesHaveNoDeviceTime() {
        List<RefinedMessage> dropped = refine(message(MessageType.OnLineTime, KindOfTime.Start, TimeType.Empty, TimeMarker.Empty, 5, 2, 0, 1000));
        assertThat(dropped).singleElement().satisfies(m -> assertThat(m.getDeviceTimeInMillis()).isEmpty());
    }
}
