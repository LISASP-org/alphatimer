package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.RankInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;

import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTextTest {

    private Locale original;

    @BeforeEach
    void useEnglish() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    private static DataHandlingMessage message(KindOfTime kind, MessageType type, int lane, int rank, int millis) {
        return new DataHandlingMessage(LocalDateTime.of(2026, 1, 1, 10, 0), "", "raw1", "raw2", type, kind, TimeType.Empty, new UsedLanes(),
                                       (byte) 2, (short) 3, (byte) 2, (byte) rank, RankInfo.Normal, (byte) lane, (byte) 0, millis, TimeInfo.Normal, TimeMarker.Empty);
    }

    @Test
    void aProcessingErrorIsASentenceWithTheDetail() {
        assertThat(MessageText.processingError(new IllegalStateException("boom"))).isEqualTo("Error while processing received data (unexpected error: boom)");
        assertThat(MessageText.processingError(new IllegalStateException())).isEqualTo("Error while processing received data (unexpected error: IllegalStateException)");
    }

    @Test
    void describesTheInterpretedMessageWithoutTheRawText() {
        String text = MessageText.describe(message(KindOfTime.Finish, MessageType.OnLineTime, 2, 1, 25_010), 0);
        assertThat(text).isEqualTo("E3 H2 L2  Finish  OnLineTime  25.01  #1");
        assertThat(text).doesNotContain("raw1").doesNotContain("raw2");
    }

    @Test
    void theTimeOfDayOfAStartGetsTheOffsetAndRaceTimesStayAsSent() {
        int halfPastSeven = 7 * 3_600_000 + 30 * 60_000;
        assertThat(MessageText.describe(message(KindOfTime.Start, MessageType.OnLineTime, 0, 0, halfPastSeven), 2)).endsWith("  09:30:00.0");
        assertThat(MessageText.describe(message(KindOfTime.Start, MessageType.OnLineTime, 0, 0, halfPastSeven), -8)).endsWith("  23:30:00.0");
        assertThat(MessageText.describe(message(KindOfTime.Finish, MessageType.OnLineTime, 2, 0, 25_010), 2)).endsWith("  25.01");
    }

    @Test
    void omitsTheRankWhenThereIsNoneAndShowsTheTimeOfDayForStarts() {
        assertThat(MessageText.describe(message(KindOfTime.SplitTime, MessageType.OnLineTime, 4, 0, 12_340), 0)).isEqualTo("E3 H2 L4  SplitTime  OnLineTime  12.34");
        assertThat(MessageText.describe(message(KindOfTime.Start, MessageType.OnLineTime, 0, 0, 112_853_930), 0)).isEqualTo("E3 H2 L0  Start  OnLineTime  07:20:53.9");
    }
}
