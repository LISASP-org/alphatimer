package org.lisasp.alphatiming.cli.analysis;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class CaptureAnalysisTest {

    private static final String PING = "\u0001\u00129\u0014TP\u0004";
    private static final String START_1 = "\u0001\u0002\u00082S ?< 400101   0\u0004";
    private static final String START_2 = "\u0001\u0002\u0008\n1 0\u000210:12:47.03 \u0004";
    private static final String FINISH_1 = "\u0001\u0002\u00082A ?< 400101   1\u0004";
    private static final String FINISH_2 = "\u0001\u0002\u0008\n3 4\u0002    1:05.43 \u0004";
    private static final String MINUS_SPLIT_1 = "\u0001\u0002\u00082I ?< 400101   2\u0004";
    private static final String MINUS_SPLIT_2 = "\u0001\u0002\u0008\n2 1\u0002-     13.66 \u0004";
    private static final String INVALID_MESSAGE_TYPE_1 = "\u0001\u0002\u0008XS ?< 400101   0\u0004";
    private static final String DAMAGED_PING = "\u0001\u00129\u0004";
    private static final String UNKNOWN = "\u0001ABCDEFGH\u0004";

    private final CaptureStatistics statistics = new CaptureStatistics();
    private final CaptureAnalysis analysis = new CaptureAnalysis(statistics);

    private void analyse(String file, String... parts) throws IOException {
        analysis.analyse(file, new ByteArrayInputStream(String.join("", parts).getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void parsedMessagesAreCountedPerType() throws IOException {
        analyse("a.dat", PING, START_1, START_2, PING, FINISH_1, FINISH_2);

        assertThat(statistics.protocolMessages()).containsExactly(entry("OnLineTime", 2L), entry("Ping", 2L));
        assertThat(statistics.refinedMessages()).containsExactly(entry("StartMessage", 1L), entry("TimeMessage", 1L), entry("UsedLanesMessage", 2L));
        assertThat(statistics.defects()).isEmpty();
        assertThat(statistics.notImplemented()).isEmpty();
    }

    @Test
    void aDataHandlingMessageIsProcessedOnlyIfTheRefinerAcceptsItWithoutDroppingAnyPart() throws IOException {
        analyse("a.dat", PING, FINISH_1, FINISH_2, MINUS_SPLIT_1, MINUS_SPLIT_2);

        assertThat(statistics.protocolMessages()).containsExactly(entry("OnLineTime", 2L), entry("Ping", 1L));
        assertThat(statistics.processedMessages()).containsExactly(entry("OnLineTime", 1L));
    }

    @Test
    void aDataHandlingMessageTypeWithoutProcessedMessagesIsCountedAsZero() throws IOException {
        analyse("a.dat", MINUS_SPLIT_1, MINUS_SPLIT_2);

        assertThat(statistics.processedMessages()).containsExactly(entry("OnLineTime", 0L));
    }

    @Test
    void aWellFormedMessageTheRefinerDropsIsNotImplementedAndGroupedByItsFields() throws IOException {
        analyse("a.dat", MINUS_SPLIT_1, MINUS_SPLIT_2, MINUS_SPLIT_1, MINUS_SPLIT_2);
        analyse("b.dat", MINUS_SPLIT_1, MINUS_SPLIT_2);

        assertThat(statistics.notImplemented()).containsExactly(new Finding(
                FindingKind.NOT_REFINED,
                "DroppedTimeMessage (messageType=OnLineTime, kindOfTime=SplitTime, timeType=Empty, timeInfo=Normal, rankInfo=Normal, timeMarker=Minus)",
                3,
                "a.dat",
                "<SOH><STX><BS>2I ?< 400101   2<EOT> <SOH><STX><BS><LF>2 1<STX>-     13.66 <EOT>"));
        assertThat(statistics.refinedMessages()).containsExactly(entry("UsedLanesMessage", 3L));
        assertThat(statistics.defects()).isEmpty();
    }

    @Test
    void anUnknownMessageIsNotImplemented() throws IOException {
        analyse("a.dat", UNKNOWN);

        assertThat(statistics.notImplemented()).containsExactly(new Finding(FindingKind.UNKNOWN_MESSAGE, "", 1, "a.dat", "<SOH>ABCDEFGH<EOT>"));
        assertThat(statistics.defects()).isEmpty();
    }

    @Test
    void bytesOutsideAMessageAreADefect() throws IOException {
        analyse("a.dat", "\u0000\u0000", PING);

        assertThat(statistics.defects()).containsExactly(new Finding(FindingKind.UNFRAMED_DATA, "", 1, "a.dat", "<00><00>"));
        assertThat(statistics.protocolMessages()).containsExactly(entry("Ping", 1L));
    }

    @Test
    void aMessageWithAnInvalidFieldIsADefectWithItsReason() throws IOException {
        analyse("a.dat", INVALID_MESSAGE_TYPE_1);

        assertThat(statistics.defects()).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(FindingKind.INVALID_MESSAGE);
            assertThat(f.detail()).isEqualTo("DataHandlingMessage1DroppedEvent: 88 must be the ascii-representation of a digit.");
            assertThat(f.count()).isEqualTo(1);
        });
    }

    @Test
    void anUnknownMessageStartingLikeAKnownOneIsADamagedMessage() throws IOException {
        analyse("a.dat", DAMAGED_PING);

        assertThat(statistics.defects()).containsExactly(new Finding(FindingKind.DAMAGED_MESSAGE, "", 1, "a.dat", "<SOH><DC2>9<EOT>"));
        assertThat(statistics.notImplemented()).isEmpty();
    }

    @Test
    void aMessageOfPingBytesOnlyIsADamagedPing() throws IOException {
        analyse("a.dat", "\u0001\u0014T\u0004");

        assertThat(statistics.defects()).extracting(Finding::kind).containsExactly(FindingKind.DAMAGED_MESSAGE);
        assertThat(statistics.notImplemented()).isEmpty();
    }

    @Test
    void aShortMessageOfAnotherFamilyIsUnknownNotDamaged() throws IOException {
        analyse("a.dat", "\u0001\u0014L0\u0004");

        assertThat(statistics.notImplemented()).extracting(Finding::kind).containsExactly(FindingKind.UNKNOWN_MESSAGE);
        assertThat(statistics.defects()).isEmpty();
    }

    @Test
    void theTwoPartsOfADataHandlingMessageMayBeSeparatedByAPing() throws IOException {
        analyse("a.dat", FINISH_1, PING, FINISH_2);

        assertThat(statistics.defects()).isEmpty();
        assertThat(statistics.refinedMessages()).contains(entry("TimeMessage", 1L));
    }

    @Test
    void aMissingPartOfADataHandlingMessageIsADefect() throws IOException {
        analyse("a.dat", FINISH_1, START_1, START_2, FINISH_2);

        assertThat(statistics.defects()).extracting(Finding::kind, Finding::count)
                                        .containsExactly(org.assertj.core.groups.Tuple.tuple(FindingKind.MESSAGE_1_WITHOUT_MESSAGE_2, 1L),
                                                         org.assertj.core.groups.Tuple.tuple(FindingKind.MESSAGE_2_WITHOUT_MESSAGE_1, 1L));
        assertThat(statistics.refinedMessages()).containsEntry("StartMessage", 1L).doesNotContainKey("TimeMessage");
    }

    @Test
    void eachFileStartsWithoutAPendingFirstPart() throws IOException {
        analyse("a.dat", FINISH_1);
        analyse("b.dat", FINISH_2);

        assertThat(statistics.defects()).extracting(Finding::kind, Finding::firstFile)
                                        .containsExactly(org.assertj.core.groups.Tuple.tuple(FindingKind.MESSAGE_1_WITHOUT_MESSAGE_2, "a.dat"),
                                                         org.assertj.core.groups.Tuple.tuple(FindingKind.MESSAGE_2_WITHOUT_MESSAGE_1, "b.dat"));
        assertThat(statistics.files()).containsExactly(new AnalysedFile("a.dat", 20), new AnalysedFile("b.dat", 21));
    }
}
