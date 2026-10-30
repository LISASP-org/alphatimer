package org.lisasp.alphatiming.legacy.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacySerializationTest {

    @Test
    void readsTheHeatsAlphaServerWrites() throws IOException {
        Heat[] heats;
        try (InputStream in = Files.newInputStream(Path.of("src/test/resources/alphaserver-heats.xml"))) {
            heats = LegacySerialization.fromXML(in);
        }

        Heat expected = new Heat("1", 3, 2);
        expected.store(0, 3392000L, LaneStatus.RaceTimes);
        expected.store(2, 3500000L, LaneStatus.RaceTimes);
        expected.store(2, 3512000L, LaneStatus.OfficialEnd);
        assertThat(heats).containsExactly(expected, new Heat("2", 4, 1));
    }

    @Test
    void readsWhatItWrites() throws IOException {
        Heat heat = new Heat("7", 12, 3);
        heat.store(1, 61230L, LaneStatus.RaceTimes);
        heat.store(4, 65000L, LaneStatus.Disq);

        assertThat(read(LegacySerialization.toXML(new Heat[] {heat}))).containsExactly(heat);
    }

    @Test
    void readsNoHeats() throws IOException {
        assertThat(read(LegacySerialization.toXML(new Heat[0]))).isEmpty();
    }

    @Test
    void keepsAnEmptyLastLane() throws IOException {
        Heat[] heats = read("""
                <AlphaServer.Heat-array>
                  <AlphaServer.Heat event="1" heat="1" id="1">
                    <lanes>
                      <entry><int>0</int><AlphaServer.Lane laneindex="0"><times/><stati/></AlphaServer.Lane></entry>
                    </lanes>
                  </AlphaServer.Heat>
                </AlphaServer.Heat-array>""");

        assertThat(heats[0].getLanes()).containsOnlyKeys(0);
    }

    @Test
    void rejectsAnotherDocument() {
        assertInvalid("<heats/>");
    }

    @Test
    void rejectsMalformedXml() {
        assertInvalid("<AlphaServer.Heat-array>");
    }

    @Test
    void rejectsADoctype() {
        assertInvalid("""
                <!DOCTYPE AlphaServer.Heat-array [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <AlphaServer.Heat-array>&x;</AlphaServer.Heat-array>""");
    }

    @Test
    void rejectsAnEventThatIsNoNumber() {
        assertInvalid(heat("event=\"x\" heat=\"1\" id=\"1\"", ""));
    }

    @Test
    void rejectsAMissingHeatNumber() {
        assertInvalid(heat("event=\"1\" id=\"1\"", ""));
    }

    @Test
    void rejectsAnUnknownLaneStatus() {
        assertInvalid(heat("event=\"1\" heat=\"1\" id=\"1\"",
                lane(0, 0, "<long>1000</long>", "<AlphaServer.LaneStatus>Fast</AlphaServer.LaneStatus>")));
    }

    @Test
    void rejectsTimesWithoutTheirStatus() {
        assertInvalid(heat("event=\"1\" heat=\"1\" id=\"1\"", lane(0, 0, "<long>1000</long>", "")));
    }

    @Test
    void rejectsATimeThatIsNoNumber() {
        assertInvalid(heat("event=\"1\" heat=\"1\" id=\"1\"",
                lane(0, 0, "<long>1:00</long>", "<AlphaServer.LaneStatus>RaceTimes</AlphaServer.LaneStatus>")));
    }

    @Test
    void rejectsALaneStoredUnderAnotherNumber() {
        assertInvalid(heat("event=\"1\" heat=\"1\" id=\"1\"", lane(1, 2, "", "")));
    }

    @Test
    void rejectsANegativeLane() {
        assertInvalid(heat("event=\"1\" heat=\"1\" id=\"1\"", lane(-1, -1, "", "")));
    }

    @Test
    void rejectsALaneTwice() {
        assertInvalid(heat("event=\"1\" heat=\"1\" id=\"1\"", lane(0, 0, "", "") + lane(0, 0, "", "")));
    }

    private static String heat(String attributes, String lanes) {
        return "<AlphaServer.Heat-array><AlphaServer.Heat " + attributes + "><lanes>" + lanes
                + "</lanes></AlphaServer.Heat></AlphaServer.Heat-array>";
    }

    private static String lane(int key, int index, String times, String stati) {
        return "<entry><int>" + key + "</int><AlphaServer.Lane laneindex=\"" + index + "\"><times>" + times
                + "</times><stati>" + stati + "</stati></AlphaServer.Lane></entry>";
    }

    private static void assertInvalid(String xml) {
        assertThatThrownBy(() -> read(xml)).isInstanceOf(InvalidLegacyXmlException.class);
    }

    private static Heat[] read(String xml) throws IOException {
        return LegacySerialization.fromXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
