package org.lisasp.alphatiming.legacy.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class AresResultFileTest {

    private static final String HEADER =
            "event;round;heat;lap;lane;idStatus;rank;time;result;mod;btime;bresult;bmod;dtime;dresult;dmod\n";

    @TempDir
    Path folder;

    @Test
    void readsTheFinishTimesOfEachHeat() throws IOException {
        Heat[] heats = read(HEADER
                + "1;0;0;0;0;0;0;57550360;\" 15:59:10.36\";\" \";0;\"\";\"\";0;\"\";\"\";\n"
                + "1;0;0;50;1;0;1;33920;\" 33.92\";\" \";0;\"\";\"\";0;\"\";\"\";\n"
                + "1;0;0;50;3;0;2;34010;\" 34.01\";\" \";0;\"\";\"\";0;\"\";\"\";\n"
                + "2;0;1;50;2;0;1;61230;\" 1:01.23\";\" \";0;\"\";\"\";0;\"\";\"\";\n");

        Heat first = new Heat("1", 1, 1);
        first.store(0, 33920, LaneStatus.RaceTimes);
        first.store(2, 34010, LaneStatus.RaceTimes);
        Heat second = new Heat("2", 2, 2);
        second.store(1, 61230, LaneStatus.RaceTimes);
        assertThat(heats).containsExactly(first, second);
    }

    @Test
    void storesTheBackupTimeBeforeTheTime() throws IOException {
        Heat[] heats = read(HEADER + "1;0;0;50;1;0;1;33920;\" 33.92\";\" \";33950;\" 33.95\";\" \";0;\"\";\"\";\n");

        assertThat(heats[0].getLanes().get(0).getTimes()).containsExactly(33950, 33920);
    }

    @Test
    void readsCommaSeparatedFiles() throws IOException {
        Heat[] heats = read(HEADER.replace(';', ',') + "4,0,2,50,6,0,1,40000,\" 40.00\",\" \",0,\"\",\"\",0,\"\",\"\",\n");

        assertThat(heats).extracting(Heat::getEvent, Heat::getHeat).containsExactly(tuple(4, 3));
        assertThat(heats[0].getLanes().get(5).getTimes()).containsExactly(40000);
    }

    @Test
    void skipsRowsWithoutLaneEventOrHeat() throws IOException {
        Heat[] heats = read(HEADER
                + "x;0;0;50;1;0;1;33920\n"
                + "1;0;y;50;1;0;1;33920\n"
                + "1;0;0;50;z;0;1;33920\n"
                + "1;0;0;50\n");

        assertThat(heats).isEmpty();
    }

    @Test
    void startsAHeatWithoutAValidTime() throws IOException {
        Heat[] heats = read(HEADER + "1;0;0;50;1;0;1;0;\"\";\"\";0\n" + "1;0;0;50;2;0;1;-;\"\";\"\";0\n");

        assertThat(heats).containsExactly(new Heat("1", 1, 1));
    }

    @Test
    void startsANewHeatWhenAnEarlierHeatAppearsAgain() throws IOException {
        Heat[] heats = read(HEADER
                + "1;0;0;50;1;0;1;33920\n"
                + "1;0;1;50;1;0;1;34920\n"
                + "1;0;0;50;2;0;1;35920\n");

        assertThat(heats).extracting(Heat::getId, Heat::getHeat).containsExactly(
                tuple("1", 1),
                tuple("2", 2),
                tuple("3", 1));
    }

    @Test
    void readsNoHeatsFromAFileWithoutRows() throws IOException {
        assertThat(read(HEADER)).isEmpty();
        assertThat(read("")).isEmpty();
    }

    @Test
    void reportsAMissingFile() {
        assertThatThrownBy(() -> AresResultFile.read(folder.resolve("missing.csv")))
                .isInstanceOf(NoSuchFileException.class);
    }

    private Heat[] read(String content) throws IOException {
        Path file = folder.resolve("results.csv");
        Files.writeString(file, content, Charset.forName("windows-1252"));
        return AresResultFile.read(file);
    }
}
