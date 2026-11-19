package org.lisasp.alphatiming.ui.core.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.Penalty;
import org.lisasp.alphatiming.legacy.api.Heat;
import org.lisasp.alphatiming.ui.core.recording.RecordingSession;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The fixture was written by the code of project version 1; it must keep opening with the same heats. */
class Version1FixtureTest {

    @TempDir
    Path dir;

    private Path copyOfFixture() throws IOException {
        Path file = dir.resolve("version-1.timing");
        Files.copy(Path.of("src/test/resources/fixtures/version-1.timing"), file);
        return file;
    }

    private Path work() throws IOException {
        return Files.createDirectories(dir.resolve("work"));
    }

    @Test
    void theHeatsOfAVersion1FileAreShown() throws Exception {
        try (TimingDocument doc = TimingDocument.open(copyOfFixture(), work());
             RecordingSession session = new RecordingSession(doc.database(), RecordingSession.tcp(), LocalDateTime::now)) {
            List<HeatDto> heats = session.liveHeats().known();

            assertThat(heats).hasSize(1);
            HeatDto heat = heats.getFirst();
            assertThat(heat.event()).isEqualTo(1);
            assertThat(heat.heat()).isEqualTo(1);
            assertThat(heat.status()).isEqualTo(HeatStatus.Finished);
            LaneDto first = heat.lanes().get(0);
            assertThat(first.timeInMillis()).isEqualTo(61_230);
            assertThat(first.place()).isEqualTo(1);
            LaneDto second = heat.lanes().get(1);
            assertThat(second.timeInMillis()).isEqualTo(62_000);
            assertThat(second.place()).isEqualTo(2);
            LaneDto third = heat.lanes().get(2);
            assertThat(third.penalty()).isEqualTo(Penalty.DidNotStart);
            assertThat(third.place()).isZero();
        }
    }

    @Test
    void theLegacyHeatsOfAVersion1FileAreServed() throws Exception {
        try (TimingDocument doc = TimingDocument.open(copyOfFixture(), work());
             RecordingSession session = new RecordingSession(doc.database(), RecordingSession.tcp(), LocalDateTime::now)) {
            Heat[] heats = session.legacyHeats();

            assertThat(heats).hasSize(1);
            assertThat(heats[0].getLanes().get(0).getTimes()).contains(61_230);
            assertThat(heats[0].getLanes().get(1).getTimes()).contains(62_000);
        }
    }
}
