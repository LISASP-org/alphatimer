package org.lisasp.alphatiming.ui.core.recording;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.project.db.LenexVersionStore;
import org.lisasp.alphatiming.project.db.TimingDatabase;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.Input;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LenexRecordingSessionTest {

    private static final String MEET = """
            <LENEX version="3.0"><MEETS><MEET name="Test Meet" course="LCM"><SESSIONS><SESSION number="1"><EVENTS>
            <EVENT eventid="7" number="3"><SWIMSTYLE distance="100" relaycount="1" /><HEATS><HEAT heatid="10" number="2" status="OFFICIAL" /></HEATS></EVENT>
            </EVENTS></SESSION></SESSIONS><CLUBS><CLUB name="Club A"><ATHLETES><ATHLETE athleteid="1" firstname="Anna" lastname="Example"><RESULTS>
            <RESULT resultid="100" eventid="7" heatid="10" lane="4" swimtime="00:01:02.34" />
            </RESULTS></ATHLETE></ATHLETES></CLUB></CLUBS></MEET></MEETS></LENEX>
            """;

    @TempDir
    Path dir;

    private TimingDatabase db;
    private RecordingSession session;
    private Path file;

    @BeforeEach
    void open() throws Exception {
        db = TimingDatabase.createNew(Files.createDirectories(dir.resolve("work")));
        session = new RecordingSession(db, RecordingSession.standard(), LocalDateTime::now);
        file = dir.resolve("meet.lef");
    }

    @AfterEach
    void close() {
        session.close();
        db.closeAndDelete();
    }

    private Settings lenex() {
        return new Settings("", 8585, 60, 1999).withCompetitionKey("WK").withConnectionType(ConnectionType.LENEX).withLenex(file.toString(), 1);
    }

    @Test
    void aLenexRecordingFeedsTheHeatsAndIsConnectedWhileItRuns() throws Exception {
        Files.writeString(file, MEET);
        List<LenexChanges> recorded = new CopyOnWriteArrayList<>();
        session.addLenexListener(recorded::add);

        session.start(lenex());

        assertThat(session.state()).isEqualTo(RecordingState.RUNNING);
        await().atMost(Duration.ofSeconds(5)).until(() -> !recorded.isEmpty());
        assertThat(session.liveHeats().known()).extracting(HeatDto::event, HeatDto::heat).containsExactly(org.assertj.core.groups.Tuple.tuple(3, 2));
        assertThat(session.isConnected()).isTrue();
        session.stop();
        assertThat(session.state()).isEqualTo(RecordingState.STOPPED);
        assertThat(session.isConnected()).isFalse();
    }

    @Test
    void aProjectWithRecordedVersionsShowsItsHeatsWhenItIsOpened() {
        new LenexVersionStore(db).record(LocalDateTime.of(2026, 1, 10, 10, 0), MEET.getBytes(StandardCharsets.UTF_8), true);

        try (RecordingSession opened = new RecordingSession(db, RecordingSession.standard(), LocalDateTime::now)) {
            assertThat(opened.liveHeats().known()).hasSize(1);
        }
    }

    @Test
    void theRecordedInputIsKnownFromTheDataAndTheFirstVersion() throws Exception {
        assertThat(session.recordedInput()).isEmpty();
        Files.writeString(file, MEET);

        session.start(lenex());
        await().atMost(Duration.ofSeconds(5)).until(() -> session.recordedInput().isPresent());

        assertThat(session.recordedInput()).contains(Input.LENEX);
        try (RecordingSession opened = new RecordingSession(db, RecordingSession.standard(), LocalDateTime::now)) {
            assertThat(opened.recordedInput()).contains(Input.LENEX);
        }
    }

    @Test
    void lenexIsNotLive() {
        assertThat(RecordingSession.isLive(lenex())).isFalse();
        assertThat(RecordingSession.isLive(lenex().withConnectionType(ConnectionType.TCP))).isTrue();
    }
}
