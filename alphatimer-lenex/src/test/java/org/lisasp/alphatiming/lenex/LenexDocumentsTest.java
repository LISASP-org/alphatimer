package org.lisasp.alphatiming.lenex;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LenexDocumentsTest {

    private static final String FILE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <LENEX created="2026-01-10T10:00:00" version="3.0">
              <MEETS>
                <MEET name="Test Meet" course="LCM">
                  <SESSIONS>
                    <SESSION number="1">
                      <EVENTS>
                        <EVENT eventid="7" number="3">
                          <SWIMSTYLE distance="100" relaycount="1" />
                          <HEATS>
                            <HEAT heatid="10" number="2" status="OFFICIAL" />
                          </HEATS>
                        </EVENT>
                      </EVENTS>
                    </SESSION>
                  </SESSIONS>
                </MEET>
              </MEETS>
            </LENEX>
            """;

    @Test
    void oneLineDropsTheFormattingBetweenTheElements() throws IOException {
        byte[] line = LenexDocuments.oneLine(bytes(FILE));

        String text = new String(line, StandardCharsets.UTF_8);
        assertThat(text).doesNotContain("\n").contains("<MEETS><MEET");
        assertThat(LenexReader.read(new ByteArrayInputStream(line))).isEqualTo(LenexReader.read(new ByteArrayInputStream(bytes(FILE))));
    }

    @Test
    void aZippedFileGivesTheSameLine() throws IOException {
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipped)) {
            zip.putNextEntry(new ZipEntry("meet.lef"));
            zip.write(bytes(FILE));
            zip.closeEntry();
        }

        assertThat(LenexDocuments.oneLine(zipped.toByteArray())).isEqualTo(LenexDocuments.oneLine(bytes(FILE)));
    }

    @Test
    void aMalformedFileIsRejected() {
        assertThatThrownBy(() -> LenexDocuments.oneLine(bytes("<LENEX><MEETS>"))).isInstanceOf(InvalidLenexException.class);
    }

    @Test
    void theExportTimeAloneIsNoChange() throws IOException {
        byte[] first = LenexDocuments.oneLine(bytes(FILE));
        byte[] exportedAgain = LenexDocuments.oneLine(bytes(FILE.replace("2026-01-10T10:00:00", "2026-01-10T10:05:00")));
        byte[] renamed = LenexDocuments.oneLine(bytes(FILE.replace("Test Meet", "Other Meet")));

        assertThat(LenexDocuments.sameExceptExportTime(first, exportedAgain)).isTrue();
        assertThat(LenexDocuments.sameExceptExportTime(first, renamed)).isFalse();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
