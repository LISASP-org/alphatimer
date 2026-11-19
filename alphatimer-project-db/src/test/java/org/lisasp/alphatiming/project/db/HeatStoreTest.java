package org.lisasp.alphatiming.project.db;

import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatService;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.TestMessageCreator;
import org.lisasp.alphatiming.heats.Penalty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lisasp.alphatiming.heats.TestMessageCreator.T0;

class HeatStoreTest {

    @TempDir
    Path dir;
    @Test
    void aMessageThatCannotBeStoredIsReportedAsAnError() throws Exception {
        TimingDatabase db = TimingDatabase.createNew(dir);
        List<Exception> errors = new ArrayList<>();
        HeatService service = new HeatService(new HeatStore(db), errors::add);
        db.close();
        service.accept(TestMessageCreator.used(1, 1, "1100000000"));
        assertThat(errors).hasSize(1);
        TimingWorkFolder.discard(db.workFile());
    }

    @Test
    void stateSurvivesSaveAndReopen() throws Exception {
        Path file = dir.resolve("x.heat");
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatService service = new HeatService(new HeatStore(db));
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.start(1, 1, T0));
            service.accept(TestMessageCreator.fin(1, 1, 1, 0, 1900));
            service.accept(TestMessageCreator.end(1, 1, T0.plusSeconds(20)));
            db.snapshotTo(file);
            db.closeAndDelete();
        }
        Path work = Files.createDirectories(dir.resolve("w"));
        try (TimingDatabase db = TimingDatabase.openCopy(file, work)) {
            HeatDto heat = new HeatStore(db).find(1, 1).orElseThrow();
            assertThat(heat.status()).isEqualTo(HeatStatus.Finished);
            assertThat(heat.ended()).isEqualTo(T0.plusSeconds(20));
            assertThat(heat.lastMessage()).isEqualTo(T0.plusSeconds(20));
            assertThat(heat.lanes().get(0)).isEqualTo(new LaneDto(1, 1900, 2, LaneStatus.Used, Penalty.None, 1, true));
            db.closeAndDelete();
        }
    }

    @Test
    void findLatestReturnsTheMostRecentlyChangedHeat() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.used(1, 2, "1100000000"));
            service.accept(TestMessageCreator.start(1, 1, T0));
            assertThat(store.findLatest().orElseThrow().heat()).isEqualTo(1);
            db.closeAndDelete();
        }
    }

    @Test
    void findRecentReturnsTheLatestChangedHeatsNewestFirst() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            assertThat(store.findRecent(2)).isEmpty();
            HeatService service = new HeatService(store);
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.used(1, 2, "1100000000"));
            service.accept(TestMessageCreator.used(1, 3, "1100000000"));
            assertThat(store.findRecent(2)).extracting(HeatDto::heat).containsExactly(3, 2);
            assertThat(store.findRecent(5)).extracting(HeatDto::heat).containsExactly(3, 2, 1);
            db.closeAndDelete();
        }
    }
}
