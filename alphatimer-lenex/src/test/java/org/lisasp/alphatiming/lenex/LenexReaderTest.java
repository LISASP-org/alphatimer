package org.lisasp.alphatiming.lenex;

import org.lisasp.alphatiming.lenex.api.LenexMeet;
import org.lisasp.alphatiming.lenex.api.LenexEvent;
import org.lisasp.alphatiming.lenex.api.LenexHeat;
import org.lisasp.alphatiming.lenex.api.LenexResult;
import org.lisasp.alphatiming.lenex.api.LenexSplit;
import org.lisasp.alphatiming.lenex.api.InvalidLenexException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class LenexReaderTest {

    @Test
    void readsTheFinishAndSplitTimesOfAResultIntoItsHeat() throws IOException {
        LenexMeet meet = read("""
                <LENEX version="3.0">
                  <MEETS>
                    <MEET name="Test Meet" course="LCM">
                      <SESSIONS>
                        <SESSION number="1" date="2026-01-10">
                          <EVENTS>
                            <EVENT eventid="7" number="3">
                              <SWIMSTYLE distance="100" relaycount="1" stroke="FREE" />
                              <HEATS>
                                <HEAT heatid="10" number="2" status="OFFICIAL" />
                              </HEATS>
                            </EVENT>
                          </EVENTS>
                        </SESSION>
                      </SESSIONS>
                      <CLUBS>
                        <CLUB name="Club A">
                          <ATHLETES>
                            <ATHLETE athleteid="1" firstname="Anna" lastname="Example">
                              <RESULTS>
                                <RESULT resultid="100" eventid="7" heatid="10" lane="4" swimtime="00:01:02.34">
                                  <SPLITS>
                                    <SPLIT distance="50" swimtime="00:00:30.12" />
                                  </SPLITS>
                                </RESULT>
                              </RESULTS>
                            </ATHLETE>
                          </ATHLETES>
                        </CLUB>
                      </CLUBS>
                    </MEET>
                  </MEETS>
                </LENEX>
                """);

        assertThat(meet.events()).containsExactly(
                new LenexEvent(3, "", "LCM", 100, 1, List.of(
                        new LenexHeat(2, "OFFICIAL", List.of(
                                new LenexResult(4, OptionalInt.of(62340), "", List.of(new LenexSplit(50, 30120))))))));
    }

    @Test
    void readsRelayResultsLikeAthleteResults() throws IOException {
        LenexMeet meet = read(meetWith("""
                <CLUB name="Club A">
                  <RELAYS>
                    <RELAY number="1">
                      <RESULTS>
                        <RESULT resultid="200" eventid="7" heatid="10" lane="5" swimtime="00:04:01.00" />
                      </RESULTS>
                    </RELAY>
                  </RELAYS>
                </CLUB>
                """));

        assertThat(onlyHeat(meet).results()).extracting(LenexResult::lane, LenexResult::swimTimeInMillis)
                                            .containsExactly(tuple(5, OptionalInt.of(241000)));
    }

    @Test
    void readsTheRoundOfAnEvent() throws IOException {
        LenexMeet meet = read(meetWith("").replace("<EVENT eventid=\"7\" number=\"3\">", "<EVENT eventid=\"7\" number=\"3\" round=\"FIN\">"));

        assertThat(meet.events()).extracting(LenexEvent::round).containsExactly("FIN");
    }

    @Test
    void readsTheFirstLaneOfThePool() throws IOException {
        LenexMeet withPool = read(meetWith("").replace("<SESSIONS>", "<POOL lanemin=\"0\" lanemax=\"7\" /><SESSIONS>"));
        LenexMeet withoutPool = read(meetWith(""));

        assertThat(withPool.laneMin()).isEqualTo(OptionalInt.of(0));
        assertThat(withoutPool.laneMin()).isEmpty();
    }

    @Test
    void theCourseOfTheSessionOverridesTheCourseOfTheMeet() throws IOException {
        LenexMeet meet = read(meetWith("").replace("<SESSION number=\"1\"", "<SESSION number=\"1\" course=\"SCM\""));

        assertThat(meet.events()).extracting(LenexEvent::course).containsExactly("SCM");
    }

    @Test
    void keepsTheStatusOfAResult() throws IOException {
        LenexMeet meet = read(meetWith(athleteResult("lane=\"4\" swimtime=\"00:01:05.00\" status=\"DSQ\"")));

        assertThat(onlyHeat(meet).results()).extracting(LenexResult::status).containsExactly("DSQ");
    }

    @Test
    void skipsResultsWithoutHeatOrLane() throws IOException {
        LenexMeet meet = read(meetWith(athleteResult("swimtime=\"00:00:00.00\" status=\"WDR\"").replace("heatid=\"10\" ", "")
                                       + athleteResult("swimtime=\"00:01:05.00\"")
                                       + athleteResult("lane=\"6\" swimtime=\"00:01:06.00\"")));

        assertThat(onlyHeat(meet).results()).extracting(LenexResult::lane).containsExactly(6);
    }

    @Test
    void noTimeIsEmpty() throws IOException {
        LenexMeet meet = read(meetWith(athleteResult("lane=\"4\" swimtime=\"NT\" status=\"DNS\"")
                                       + athleteResult("lane=\"5\" swimtime=\"00:00:00.00\" status=\"DNS\"")));

        assertThat(onlyHeat(meet).results()).extracting(LenexResult::swimTimeInMillis)
                                            .containsExactly(OptionalInt.empty(), OptionalInt.empty());
    }

    @Test
    void rejectsXmlThatIsNotWellFormed() {
        assertThatThrownBy(() -> read("<LENEX version=\"3.0\"><MEETS>")).isInstanceOf(InvalidLenexException.class);
    }

    @Test
    void rejectsADoctype() {
        assertThatThrownBy(() -> read("<!DOCTYPE LENEX [<!ENTITY x \"y\">]>" + meetWith("")))
                .isInstanceOf(InvalidLenexException.class);
    }

    @Test
    void rejectsAFileWithoutAMeet() {
        assertThatThrownBy(() -> read("<LENEX version=\"3.0\" />")).isInstanceOf(InvalidLenexException.class);
    }

    @Test
    void skipsEventsHeatsAndSplitsThatCannotBeRead() throws IOException {
        LenexMeet meet = read(meetWith(athleteResult("lane=\"4\" swimtime=\"00:01:05.00\""))
                .replace("<SPLITS>", "")
                .replace("<HEAT heatid=\"10\" number=\"2\" status=\"OFFICIAL\" />",
                         "<HEAT heatid=\"10\" number=\"2\" status=\"OFFICIAL\" /><HEAT heatid=\"11\" number=\"x\" />")
                .replace("<EVENTS>", "<EVENTS><EVENT eventid=\"8\" number=\"4\" /><EVENT eventid=\"9\" number=\"\"><SWIMSTYLE distance=\"50\" relaycount=\"1\" /></EVENT>")
                .replace("swimtime=\"00:01:05.00\" />",
                         "swimtime=\"00:01:05.00\"><SPLITS><SPLIT distance=\"50\" swimtime=\"NT\" /><SPLIT distance=\"x\" swimtime=\"00:00:30.00\" /></SPLITS></RESULT>"));

        assertThat(meet.events()).extracting(LenexEvent::number).containsExactly(3);
        assertThat(meet.events().getFirst().heats()).extracting(LenexHeat::number).containsExactly(2);
        assertThat(onlyHeat(meet).results().getFirst().splits()).isEmpty();
    }

    @Test
    void readsAFileAndAZippedFile(@TempDir Path folder) throws IOException {
        String xml = meetWith(athleteResult("lane=\"4\" swimtime=\"00:01:05.00\""));
        Path plain = Files.writeString(folder.resolve("meet.lef"), xml);
        Path zipped = folder.resolve("meet.lxf");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipped))) {
            zip.putNextEntry(new ZipEntry("meet.lef"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        LenexMeet expected = read(xml);
        assertThat(LenexReader.read(plain)).isEqualTo(expected);
        assertThat(LenexReader.read(zipped)).isEqualTo(expected);
    }

    @Test
    void findsTheHeatOfAResultByItsHeatIdEvenIfTheResultNamesThePreviousEvent() throws IOException {
        LenexMeet meet = read(meetWith(athleteResult("lane=\"4\" swimtime=\"00:01:05.00\"").replace("heatid=\"10\"", "heatid=\"20\""))
                .replace("</EVENTS>", """
                        <EVENT eventid="8" number="3" round="EXTRAHEATS" preveventid="7">
                          <SWIMSTYLE distance="100" relaycount="1" stroke="FREE" />
                          <HEATS>
                            <HEAT heatid="20" number="1" status="OFFICIAL" />
                          </HEATS>
                        </EVENT>
                        </EVENTS>"""));

        assertThat(meet.events().get(1).heats().getFirst().results()).extracting(LenexResult::lane).containsExactly(4);
    }

    @Test
    void readsAZippedFileWhoseEntryNameIsNotUtf8(@TempDir Path folder) throws IOException {
        String xml = meetWith(athleteResult("lane=\"4\" swimtime=\"00:01:05.00\""));
        Path zipped = folder.resolve("meet.lxf");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipped), Charset.forName("windows-1251"))) {
            zip.putNextEntry(new ZipEntry("приглашение.lef"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThat(LenexReader.read(zipped)).isEqualTo(read(xml));
    }

    private static LenexHeat onlyHeat(LenexMeet meet) {
        return meet.events().getFirst().heats().getFirst();
    }

    private static String athleteResult(String attributes) {
        return """
                <CLUB name="Club A">
                  <ATHLETES>
                    <ATHLETE athleteid="1" firstname="Anna" lastname="Example">
                      <RESULTS>
                        <RESULT resultid="100" eventid="7" heatid="10" %s />
                      </RESULTS>
                    </ATHLETE>
                  </ATHLETES>
                </CLUB>
                """.formatted(attributes);
    }

    private static String meetWith(String clubs) {
        return """
                <LENEX version="3.0">
                  <MEETS>
                    <MEET name="Test Meet" course="LCM">
                      <SESSIONS>
                        <SESSION number="1" date="2026-01-10">
                          <EVENTS>
                            <EVENT eventid="7" number="3">
                              <SWIMSTYLE distance="100" relaycount="1" stroke="FREE" />
                              <HEATS>
                                <HEAT heatid="10" number="2" status="OFFICIAL" />
                              </HEATS>
                            </EVENT>
                          </EVENTS>
                        </SESSION>
                      </SESSIONS>
                      <CLUBS>
                %s
                      </CLUBS>
                    </MEET>
                  </MEETS>
                </LENEX>
                """.formatted(clubs);
    }

    private static LenexMeet read(String xml) throws IOException {
        return LenexReader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
