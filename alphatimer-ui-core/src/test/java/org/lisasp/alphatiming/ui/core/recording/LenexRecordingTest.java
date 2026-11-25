package org.lisasp.alphatiming.ui.core.recording;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatiming.project.db.LenexVersionStore;
import org.lisasp.alphatiming.project.db.TimingDatabase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LenexRecordingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 10, 10, 0);

    @TempDir
    Path dir;

    private TimingDatabase db;
    private LenexVersionStore store;
    private Path file;
    private final List<RefinedMessage> messages = new ArrayList<>();
    private final List<LenexChanges> recorded = new ArrayList<>();
    private int signsOfLife;
    private final List<WriteListener> writeListeners = new ArrayList<>();

    @BeforeEach
    void open() throws IOException {
        db = TimingDatabase.createNew(Files.createDirectories(dir.resolve("work")));
        store = new LenexVersionStore(db);
        file = dir.resolve("meet.lef");
    }

    @AfterEach
    void close() {
        db.close();
    }

    private LenexRecording recording() {
        return new LenexRecording(file, store, messages::add, () -> NOW, new LenexRecording.Listener() {
            @Override
            public void signOfLife() {
                signsOfLife++;
            }

            @Override
            public void recorded(LenexChanges changes) {
                recorded.add(changes);
            }
        }, new WriteMonitor(writeListeners));
    }

    @Test
    void theFirstCheckRecordsTheFileAndFeedsTheHeats() throws IOException {
        write(meet("2026-01-10T10:00:00", 62340), 1);
        LenexRecording recording = recording();

        recording.check();

        assertThat(store.findAll()).hasSize(1);
        assertThat(messages).filteredOn(TimeMessage.class::isInstance).hasSize(1);
        assertThat(recorded).containsExactly(new LenexChanges(messages.size(), 1));
        assertThat(signsOfLife).isEqualTo(1);
        assertThat(recording.isConnected()).isTrue();
    }

    @Test
    void anUnchangedFileIsOnlyASignOfLife() throws IOException {
        write(meet("2026-01-10T10:00:00", 62340), 1);
        LenexRecording recording = recording();
        recording.check();
        messages.clear();

        recording.check();

        assertThat(messages).isEmpty();
        assertThat(store.findAll()).hasSize(1);
        assertThat(signsOfLife).isEqualTo(2);
    }

    @Test
    void aMalformedFileIsNotRecordedAndReadAgainUntilItIsComplete() throws IOException {
        write(meet("2026-01-10T10:00:00", 62340).substring(0, 120), 1);
        LenexRecording recording = recording();
        recording.check();
        assertThat(store.findAll()).isEmpty();

        write(meet("2026-01-10T10:00:00", 62340), 1);
        recording.check();

        assertThat(store.findAll()).hasSize(1);
    }

    @Test
    void aNewExportTimeAloneIsNotRecorded() throws IOException {
        write(meet("2026-01-10T10:00:00", 62340), 1);
        LenexRecording recording = recording();
        recording.check();

        write(meet("2026-01-10T10:05:00", 62340), 2);
        recording.check();

        assertThat(store.findAll()).hasSize(1);
        assertThat(recorded).hasSize(1);
    }

    @Test
    void aMissingFileIsNotConnected() {
        LenexRecording recording = recording();

        recording.check();

        assertThat(recording.isConnected()).isFalse();
        assertThat(signsOfLife).isZero();
    }

    @Test
    void theRecordingGoesOnFromTheLastRecordedVersion() throws IOException {
        write(meet("2026-01-10T10:00:00", 62340), 1);
        recording().check();
        messages.clear();

        LenexRecording again = recording();
        again.check();
        write(meet("2026-01-10T10:00:00", 62350), 2);
        again.check();

        assertThat(messages).filteredOn(TimeMessage.class::isInstance).hasSize(2);
    }

    @Test
    void aVersionThatCannotBeStoredIsReportedAndRecordedAtTheNextCheck() throws IOException {
        List<String> writes = new ArrayList<>();
        writeListeners.add(new WriteListener() {
            @Override
            public void writeFailed(RecordedData data, Exception cause) {
                writes.add("failed " + data);
            }

            @Override
            public void writeRecovered(RecordedData data) {
                writes.add("recovered " + data);
            }

            @Override
            public void dataDropped(RecordedData data, int bytes) {
            }
        });
        write(meet("2026-01-10T10:00:00", 62340), 1);
        LenexRecording recording = recording();
        db.write(em -> em.createNativeQuery("CREATE TRIGGER block BEFORE INSERT ON lenexversion BEGIN SELECT RAISE(ABORT, 'disk full'); END").executeUpdate());

        recording.check();

        assertThat(writes).containsExactly("failed LENEX_VERSIONS");
        assertThat(messages).isEmpty();
        assertThat(store.findAll()).isEmpty();

        db.write(em -> em.createNativeQuery("DROP TRIGGER block").executeUpdate());
        recording.check();

        assertThat(writes).containsExactly("failed LENEX_VERSIONS", "recovered LENEX_VERSIONS");
        assertThat(messages).filteredOn(TimeMessage.class::isInstance).hasSize(1);
        assertThat(store.findAll()).hasSize(1);
    }

    private void write(String content, int second) throws IOException {
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-01-10T10:00:00Z").plusSeconds(second)));
    }

    private static String meet(String created, int finishMillis) {
        return """
                <LENEX created="%s" version="3.0">
                  <MEETS>
                    <MEET name="Test Meet" course="LCM">
                      <SESSIONS>
                        <SESSION number="1">
                          <EVENTS>
                            <EVENT eventid="7" number="3">
                              <SWIMSTYLE distance="100" relaycount="1" />
                              <HEATS>
                                <HEAT heatid="10" number="2" />
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
                                <RESULT resultid="100" eventid="7" heatid="10" lane="4" swimtime="%s" />
                              </RESULTS>
                            </ATHLETE>
                          </ATHLETES>
                        </CLUB>
                      </CLUBS>
                    </MEET>
                  </MEETS>
                </LENEX>
                """.formatted(created, String.format("00:%02d:%02d.%02d", finishMillis / 60000, finishMillis / 1000 % 60, finishMillis % 1000 / 10));
    }
}
