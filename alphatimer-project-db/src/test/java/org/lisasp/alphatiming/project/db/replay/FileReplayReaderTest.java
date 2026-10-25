package org.lisasp.alphatiming.project.db.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatiming.project.db.TimingDatabase;
import org.lisasp.alphatiming.project.db.RawDataChunkEntity;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class FileReplayReaderTest {

    @TempDir
    Path dir;

    private static byte[] sample(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i * 7 + 3);
        }
        return data;
    }

    private static byte[] collect(CopyOnWriteArrayList<Byte> received) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        received.forEach(out::write);
        return out.toByteArray();
    }

    @Test
    void replaysAPureBinaryFileInOrder() throws Exception {
        Path file = dir.resolve("run.serial");
        byte[] data = sample(50);
        Files.write(file, data);
        CopyOnWriteArrayList<Byte> received = new CopyOnWriteArrayList<>();
        AtomicBoolean finished = new AtomicBoolean();
        FileReplayReader reader = new FileReplayReader(file, 96_000);
        reader.register(received::add);
        reader.addFinishedListener(() -> finished.set(true));

        reader.start();

        await().atMost(Duration.ofSeconds(5)).untilTrue(finished);
        assertThat(collect(received)).isEqualTo(data);
        reader.close();
    }

    @Test
    void replaysTheRawDataOfAnotherHeatFile() throws Exception {
        Path heat = dir.resolve("other.heat");
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            db.write(em -> {
                em.persist(new RawDataChunkEntity(LocalDateTime.now(), new byte[]{1, 2, 3}));
                em.persist(new RawDataChunkEntity(LocalDateTime.now(), new byte[]{4, 5}));
                return null;
            });
            db.snapshotTo(heat);
            db.closeAndDelete();
        }
        CopyOnWriteArrayList<Byte> received = new CopyOnWriteArrayList<>();
        AtomicBoolean finished = new AtomicBoolean();
        FileReplayReader reader = new FileReplayReader(heat, 96_000);
        reader.register(received::add);
        reader.addFinishedListener(() -> finished.set(true));
        reader.start();
        await().atMost(Duration.ofSeconds(5)).untilTrue(finished);
        assertThat(collect(received)).containsExactly(1, 2, 3, 4, 5);
        reader.close();
    }

    @Test
    void replaysAt9600BaudWhichAreAbout960BytesPerSecond() throws Exception {
        Path file = dir.resolve("paced.serial");
        Files.write(file, sample(192));
        AtomicBoolean finished = new AtomicBoolean();
        FileReplayReader reader = new FileReplayReader(file, 9600);
        reader.addFinishedListener(() -> finished.set(true));
        long begin = System.nanoTime();
        reader.start();
        await().atMost(Duration.ofSeconds(5)).untilTrue(finished);
        long millis = Duration.ofNanos(System.nanoTime() - begin).toMillis();
        assertThat(millis).as("192 bytes at 960 bytes per second").isBetween(170L, 1500L);
        reader.close();
    }

    @Test
    void closeStopsAReplayInProgressPromptly() throws Exception {
        Path file = dir.resolve("long.serial");
        Files.write(file, sample(9600));
        CopyOnWriteArrayList<Byte> received = new CopyOnWriteArrayList<>();
        FileReplayReader reader = new FileReplayReader(file, 9600);
        reader.register(received::add);
        reader.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> !received.isEmpty());
        assertThat(reader.isConnected()).isTrue();
        reader.close();
        int atClose = received.size();
        Thread.sleep(300);
        assertThat(received.size()).isLessThanOrEqualTo(atClose + 1);
        assertThat(reader.isConnected()).isFalse();
        reader.close();
    }

    @Test
    void missingAndUnreadableFilesFailOnStartWithTheFile() throws Exception {
        FileReplayReader missing = new FileReplayReader(dir.resolve("nope.serial"), 9600);
        assertThatThrownBy(missing::start).isInstanceOfSatisfying(ReplayFileException.class, ex -> {
            assertThat(ex.reason()).isEqualTo(ReplayFileException.Reason.NOT_FOUND);
            assertThat(ex.file()).isEqualTo(dir.resolve("nope.serial"));
        });
        Path notAProject = dir.resolve("foreign.sqlite");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + notAProject); var st = connection.createStatement()) {
            st.execute("create table other (id integer)");
        }
        assertThatThrownBy(new FileReplayReader(notAProject, 9600)::start)
                .isInstanceOfSatisfying(ReplayFileException.class, ex -> assertThat(ex.reason()).isEqualTo(ReplayFileException.Reason.UNREADABLE));
        assertThatThrownBy(new FileReplayReader(dir, 9600)::start).isInstanceOf(ReplayFileException.class);
    }

    @Test
    void anEmptyFileFinishesImmediately() throws Exception {
        Path empty = dir.resolve("empty.serial");
        Files.write(empty, new byte[0]);
        AtomicBoolean finished = new AtomicBoolean();
        FileReplayReader reader = new FileReplayReader(empty, 9600);
        reader.addFinishedListener(() -> finished.set(true));
        reader.start();
        await().atMost(Duration.ofSeconds(5)).untilTrue(finished);
        reader.close();
    }

    @Test
    void aFailingConsumerDoesNotStopTheReplay() throws Exception {
        Path file = dir.resolve("run.serial");
        Files.write(file, sample(20));
        CopyOnWriteArrayList<Byte> received = new CopyOnWriteArrayList<>();
        AtomicBoolean finished = new AtomicBoolean();
        FileReplayReader reader = new FileReplayReader(file, 96_000);
        reader.register(b -> {
            if (b == 3) {
                throw new IllegalStateException("consumer bug");
            }
            received.add(b);
        });
        reader.addFinishedListener(() -> finished.set(true));
        reader.start();
        await().atMost(Duration.ofSeconds(5)).untilTrue(finished);
        assertThat(received).hasSizeGreaterThan(15);
        reader.close();
    }
}
