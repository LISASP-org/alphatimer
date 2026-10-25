package org.lisasp.alphatiming.ui.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatiming.heats.LiveHeats;
import org.lisasp.alphatiming.project.db.HeatStore;
import org.lisasp.alphatiming.ui.core.project.TimingDocument;
import org.lisasp.alphatiming.project.db.MessageStore;
import org.lisasp.alphatiming.project.db.RawDataRecorder;
import org.lisasp.alphatiming.ui.core.recording.RecordingSession;
import org.lisasp.alphatiming.ui.core.settings.Settings;
import org.lisasp.alphatiming.project.db.testdata.DataHandlingMessageTestData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class EndToEndTest {

    @TempDir
    Path dir;

    private static byte[] sample() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(DataHandlingMessageTestData.message1);
        out.writeBytes(DataHandlingMessageTestData.message2);
        return out.toByteArray();
    }

    /** Keeps the connection open until the pipeline has processed the data, so that the reader does not see a close first. */
    private static void sendOnce(ServerSocket server, byte[] data, Callable<Boolean> processed) throws Exception {
        try (Socket client = server.accept()) {
            OutputStream out = client.getOutputStream();
            out.write(data);
            out.flush();
            await().atMost(Duration.ofSeconds(10)).until(processed);
        }
    }

    private static long filesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }

    @Test
    void recordsOverTcpSavesReopensAndRecordsAgainWithChangedSettings() throws Exception {
        Path work = Files.createDirectories(dir.resolve("work"));
        Path file = dir.resolve("e2e.heat");
        byte[] data = sample();

        try (ServerSocket server = new ServerSocket(0);
             TimingDocument doc = TimingDocument.create(work, new Settings("localhost", server.getLocalPort(), 60, 0).withCompetitionKey("WK"));
             RecordingSession session = new RecordingSession(doc.database(), RecordingSession.tcp(), LocalDateTime::now)) {
            CopyOnWriteArrayList<LiveHeats> heats = new CopyOnWriteArrayList<>();
            session.addHeatListener(heats::add);

            session.start(doc.settings());
            sendOnce(server, data, () -> !heats.isEmpty());
            session.stop();
            doc.saveAs(file);
        }
        assertThat(filesIn(work)).isZero();

        try (ServerSocket server = new ServerSocket(0);
             TimingDocument doc = TimingDocument.open(file, work);
             RecordingSession session = new RecordingSession(doc.database(), RecordingSession.tcp(), LocalDateTime::now)) {
            assertThat(doc.settings().tcpHost()).isEqualTo("localhost");
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            RawDataRecorder.readAll(doc.database()).forEach(raw::writeBytes);
            assertThat(raw.toByteArray()).isEqualTo(data);
            assertThat(new MessageStore(doc.database()).findBy("", (short) 1, (byte) 1)).hasSize(1);
            assertThat(new HeatStore(doc.database()).findLatest()).isPresent();
            assertThat(doc.isDirty()).isFalse();

            // change the settings without restarting the program, record again
            doc.updateSettings(doc.settings().withTcp("localhost", server.getLocalPort()));
            assertThat(doc.isDirty()).isTrue();
            session.start(doc.settings());
            sendOnce(server, data, () -> new MessageStore(doc.database()).findBy("", (short) 1, (byte) 1).size() == 2);
            session.stop();
            ByteArrayOutputStream raw2 = new ByteArrayOutputStream();
            RawDataRecorder.readAll(doc.database()).forEach(raw2::writeBytes);
            assertThat(raw2.size()).isEqualTo(data.length * 2);
        }
    }
}
