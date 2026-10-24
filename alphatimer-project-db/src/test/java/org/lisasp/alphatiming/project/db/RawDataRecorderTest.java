package org.lisasp.alphatiming.project.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RawDataRecorderTest {

    @TempDir
    Path dir;

    @Test
    void flushesBufferedBytesIntoChunksInOrder() throws Exception {
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            RawDataRecorder rec = new RawDataRecorder(db, () -> LocalDateTime.of(2026, 1, 1, 10, 0), 4, 60_000, e -> { });
            for (byte b = 1; b <= 10; b++) {
                rec.accept(b);
            }
            rec.close();
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            RawDataRecorder.readAll(db).forEach(all::writeBytes);
            assertThat(all.toByteArray()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            assertThat(RawDataRecorder.readAll(db)).hasSize(3);
            assertThat(db.isDirty()).isTrue();
            db.closeAndDelete();
        }
    }

    @Test
    void aStoreThatKeepsFailingIsReportedOnceAndTheBytesStayBuffered() throws Exception {
        ProjectDatabase db = ProjectDatabase.createNew(dir);
        List<Exception> errors = new ArrayList<>();
        RawDataRecorder rec = new RawDataRecorder(db, LocalDateTime::now, 1000, 60_000, errors::add);
        db.close();
        rec.accept((byte) 7);
        rec.flush();
        rec.flush();
        rec.close();
        assertThat(errors).hasSize(1);
        assertThat(rec.bufferedBytes()).isEqualTo(1);
        ProjectDatabase.discard(db.workFile());
    }

    @Test
    void timerFlushesPartialBuffer() throws Exception {
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            RawDataRecorder rec = new RawDataRecorder(db, LocalDateTime::now, 1000, 50, e -> { });
            rec.accept((byte) 7);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(RawDataRecorder.readAll(db)).hasSize(1));
            rec.close();
            db.closeAndDelete();
        }
    }
}
