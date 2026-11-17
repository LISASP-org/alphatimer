package org.lisasp.alphatiming.cli.analysis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatiming.cli.ExitCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyseCommandTest {

    private static final String PING = "\u0001\u00129\u0014TP\u0004";
    private static final String FINISH_1 = "\u0001\u0002\u00082A ?< 400101   1\u0004";
    private static final String FINISH_2 = "\u0001\u0002\u0008\n3 4\u0002    1:05.43 \u0004";
    private static final String MANUAL_SPLIT_1 = "\u0001\u0002\u00082IM?< 400101   2\u0004";
    private static final String MANUAL_SPLIT_2 = "\u0001\u0002\u0008\n2 1\u0002      13.66 \u0004";

    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final AnalyseCommand command = new AnalyseCommand(new PrintStream(out, true, StandardCharsets.UTF_8),
                                                              new PrintStream(err, true, StandardCharsets.UTF_8));
    private Locale original;

    @BeforeEach
    void english() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    private void write(String name, String... parts) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, String.join("", parts).getBytes(StandardCharsets.ISO_8859_1));
    }

    private List<String> outLines() {
        return out.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    void withoutADirectoryTheUsageIsShown() {
        assertThat(command.run(List.of())).isEqualTo(ExitCode.USAGE);

        assertThat(err.toString(StandardCharsets.UTF_8)).startsWith("Usage: ");
        assertThat(out.size()).isZero();
    }

    @Test
    void aMissingDirectoryIsReported() {
        Path missing = dir.resolve("missing");

        assertThat(command.run(List.of(missing.toString()))).isEqualTo(ExitCode.INPUT_NOT_READ);

        assertThat(err.toString(StandardCharsets.UTF_8)).contains("The directory " + missing + " does not exist");
    }

    @Test
    void theReportCoversOnlyTheDatAndSerialFilesDirectlyInTheDirectory() throws IOException {
        write("b.serial", FINISH_1, FINISH_2, MANUAL_SPLIT_1, MANUAL_SPLIT_2, "\u0000");
        write("a.DAT", PING);
        write("c.txt", PING);
        write("sub/d.dat", PING);

        assertThat(command.run(List.of(dir.toString()))).isEqualTo(ExitCode.OK);

        assertThat(outLines()).containsExactly(
                "2 files, 90 bytes",
                "",
                "Messages (pings, data handling messages by message type):",
                "                    Count  Processed",
                "  OnLineTime            2          1",
                "  Ping                  1          -",
                "",
                "Refined messages:",
                "  TimeMessage           1",
                "  UsedLanesMessage      2",
                "",
                "Defects (1):",
                "  1 x Data outside of a message",
                "      first in b.serial: <00>",
                "",
                "Well-formed, but not implemented (1):",
                "  1 x Dropped by the refiner: DroppedTimeMessage (messageType=OnLineTime, kindOfTime=SplitTime, timeType=ManualTime, timeInfo=Normal, rankInfo=Normal, timeMarker=Empty)",
                "      first in b.serial: <SOH><STX><BS>2IM?< 400101   2<EOT> <SOH><STX><BS><LF>2 1<STX>      13.66 <EOT>");
    }

    @Test
    void bothMessageTablesShareTheirColumnsAndAlignTheCountsRight() throws IOException {
        write("a.dat", PING.repeat(10), FINISH_1, FINISH_2);

        command.run(List.of(dir.toString()));

        assertThat(outLines()).containsSubsequence("                    Count  Processed",
                                                   "  OnLineTime            1          1",
                                                   "  Ping                 10          -",
                                                   "",
                                                   "Refined messages:",
                                                   "  TimeMessage           1",
                                                   "  UsedLanesMessage      1");
    }

    @Test
    void sectionsWithoutEntriesSayNone() throws IOException {
        write("a.dat", PING);

        command.run(List.of(dir.toString()));

        assertThat(outLines()).containsSubsequence("Refined messages:", "  none", "", "Defects (0):", "  none", "",
                                                   "Well-formed, but not implemented (0):", "  none");
    }
}
