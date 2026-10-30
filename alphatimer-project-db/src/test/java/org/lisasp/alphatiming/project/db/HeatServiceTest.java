package org.lisasp.alphatiming.project.db;

import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatService;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.LiveHeats;
import org.lisasp.alphatiming.heats.TestMessageCreator;
import org.lisasp.alphatiming.heats.Penalty;
import org.lisasp.alphatiming.heats.RunningClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lisasp.alphatiming.heats.TestMessageCreator.T0;

class HeatServiceTest {

    @TempDir
    Path dir;

    @Test
    void sameHeatNumberInDifferentEventsStaysSeparate() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.used(2, 1, "0011000000"));
            service.accept(TestMessageCreator.split(1, 1, 1, 1, 1000));
            assertThat(store.find(1, 1).orElseThrow().lanes().get(0).timeInMillis()).isEqualTo(1000);
            assertThat(store.find(2, 1).orElseThrow().lanes().get(0).status()).isEqualTo(LaneStatus.NotUsed);
            db.closeAndDelete();
        }
    }

    @Test
    void theTimeOfDayOfTheStartIsStoredWithTheHeat() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            new HeatService(store).accept(TestMessageCreator.startAtTimeOfDay(1, 1, T0, 36_000_000));
            assertThat(store.find(1, 1).orElseThrow().startTimeOfDayMillis()).isEqualTo(36_000_000);
            db.closeAndDelete();
        }
    }

    @Test
    void listenersGetCurrentHeatOnRegistrationAndChangesAfterwards() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatService service = new HeatService(new HeatStore(db));
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            List<HeatDto> seen = new ArrayList<>();
            service.register(h -> seen.add(h.current()));
            assertThat(seen).hasSize(1);
            service.accept(TestMessageCreator.start(1, 1, T0));
            assertThat(seen).hasSize(2);
            assertThat(seen.get(1).status()).isEqualTo(HeatStatus.Started);
            service.accept(TestMessageCreator.start(1, 1, T0)); // no change
            assertThat(seen).hasSize(2);
            db.closeAndDelete();
        }
    }

    @Test
    void theHeatThatWasCurrentBeforeBecomesThePreviousHeatWithItsLastState() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            List<LiveHeats> seen = new ArrayList<>();
            service.register(seen::add);
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.start(1, 1, T0));
            service.accept(TestMessageCreator.fin(1, 1, 1, 0, 1900));
            assertThat(seen.get(seen.size() - 1).previous()).isNull();

            service.accept(TestMessageCreator.used(1, 2, "1100000000"));
            LiveHeats afterSwitch = seen.get(seen.size() - 1);
            assertThat(afterSwitch.current().heat()).isEqualTo(2);
            assertThat(afterSwitch.previous().heat()).isEqualTo(1);
            assertThat(afterSwitch.previous().lanes().get(0).timeInMillis()).isEqualTo(1900);
            assertThat(afterSwitch.previous().lanes().get(0).place()).isEqualTo(1);

            service.accept(TestMessageCreator.start(1, 2, T0.plusSeconds(60)));
            service.accept(TestMessageCreator.split(1, 2, 1, 1, 800));
            LiveHeats during = seen.get(seen.size() - 1);
            assertThat(during.current().heat()).isEqualTo(2);
            assertThat(during.previous().heat()).isEqualTo(1);

            service.accept(TestMessageCreator.used(1, 3, "1100000000"));
            assertThat(seen.get(seen.size() - 1).previous().heat()).isEqualTo(2);
            db.closeAndDelete();
        }
    }

    @Test
    void aNewServiceStartsWithTheCurrentAndPreviousHeatOfTheProject() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService first = new HeatService(store);
            first.accept(TestMessageCreator.start(1, 1, T0));
            first.accept(TestMessageCreator.used(1, 2, "1100000000"));

            HeatService second = new HeatService(store);
            List<LiveHeats> seen = new ArrayList<>();
            second.register(seen::add);

            assertThat(seen).hasSize(1);
            assertThat(seen.get(0).current().heat()).isEqualTo(2);
            assertThat(seen.get(0).previous().heat()).isEqualTo(1);
            db.closeAndDelete();
        }
    }

    @Test
    void lanesThatWereUsedOnceStayKnownForTheLiveView() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            List<LiveHeats> seen = new ArrayList<>();
            service.register(seen::add);
            service.accept(TestMessageCreator.used(1, 1, "1110000000"));
            assertThat(seen.get(seen.size() - 1).usedLanes()).containsExactly(1, 2, 3);

            service.accept(TestMessageCreator.used(1, 2, "0100000000"));
            LiveHeats second = seen.get(seen.size() - 1);
            assertThat(second.current().lanes().get(0).status()).isEqualTo(LaneStatus.NotUsed);
            assertThat(second.usedLanes()).as("lane 1 and 3 were used before and stay known").containsExactly(1, 2, 3);

            assertThat(store.findUsedLanes()).containsExactly(1, 2, 3);
            List<LiveHeats> restored = new ArrayList<>();
            new HeatService(store).register(restored::add);
            assertThat(restored.get(0).usedLanes()).containsExactly(1, 2, 3);
            db.closeAndDelete();
        }
    }

    @Test
    void everyHeatOfTheProjectIsKnownWithItsLatestState() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService first = new HeatService(store);
            first.accept(TestMessageCreator.used(1, 1, "1100000000"));
            first.accept(TestMessageCreator.used(1, 2, "1100000000"));

            HeatService second = new HeatService(store);
            List<LiveHeats> seen = new ArrayList<>();
            second.register(seen::add);
            assertThat(seen.get(0).known()).extracting(HeatDto::heat).containsExactlyInAnyOrder(1, 2);

            second.accept(TestMessageCreator.used(1, 3, "1100000000"));
            second.accept(TestMessageCreator.start(1, 1, T0));
            List<HeatDto> known = seen.get(seen.size() - 1).known();
            assertThat(known).extracting(HeatDto::heat).containsExactlyInAnyOrder(1, 2, 3);
            assertThat(known).filteredOn(h -> h.heat() == 1).singleElement().extracting(HeatDto::status).isEqualTo(HeatStatus.Started);
            db.closeAndDelete();
        }
    }

    @Test
    void aStartedHeatWithoutEndIsEstimatedFinishedByTheFirstMessageOfAnotherHeat() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            List<LiveHeats> seen = new ArrayList<>();
            service.register(seen::add);
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.start(1, 1, T0));

            service.accept(TestMessageCreator.start(1, 2, T0.plusMinutes(3)));
            service.accept(TestMessageCreator.splitAt(1, 2, 1, 1, 1000, T0.plusMinutes(4)));

            HeatDto estimated = store.find(1, 1).orElseThrow();
            assertThat(estimated.status()).isEqualTo(HeatStatus.FinishedEstimated);
            assertThat(estimated.ended()).isEqualTo(T0.plusMinutes(3));
            assertThat(seen.get(seen.size() - 1).previous()).isEqualTo(estimated);
            assertThat(seen.get(seen.size() - 1).known()).contains(estimated);

            service.accept(TestMessageCreator.end(1, 1, T0.plusMinutes(5)));
            assertThat(store.find(1, 1).orElseThrow().status()).isEqualTo(HeatStatus.Finished);
            assertThat(store.find(1, 1).orElseThrow().ended()).isEqualTo(T0.plusMinutes(5));
            db.closeAndDelete();
        }
    }

    @Test
    void aHeatThatFellSilentLongBeforeTheNextHeatIsEstimatedToEndWhenItsClockStopped() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            service.accept(TestMessageCreator.start(1, 1, T0));

            service.accept(TestMessageCreator.start(1, 2, T0.plusMinutes(30)));

            assertThat(store.find(1, 1).orElseThrow().ended()).isEqualTo(T0.plus(RunningClock.STALE_AFTER));
            db.closeAndDelete();
        }
    }

    @Test
    void onlyAStartedHeatWithoutEndIsEstimated() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            service.accept(TestMessageCreator.used(1, 1, "1000000000"));
            service.accept(TestMessageCreator.start(1, 1, T0));
            service.accept(TestMessageCreator.finAt(1, 1, 1, 50_000, T0.plusSeconds(50)));
            service.accept(TestMessageCreator.used(1, 2, "1100000000"));

            service.accept(TestMessageCreator.start(1, 3, T0.plusMinutes(5)));

            assertThat(store.find(1, 1).orElseThrow().status()).isEqualTo(HeatStatus.Started);
            assertThat(store.find(1, 1).orElseThrow().ended()).isEqualTo(T0.plusSeconds(50));
            assertThat(store.find(1, 2).orElseThrow().status()).isEqualTo(HeatStatus.Open);
            assertThat(store.find(1, 2).orElseThrow().ended()).isNull();
            db.closeAndDelete();
        }
    }

    @Test
    void aMessageOfAnotherHeatThatChangesNothingEstimatesNothing() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.start(1, 2, T0));

            service.accept(TestMessageCreator.used(1, 1, "1100000000"));

            assertThat(store.find(1, 2).orElseThrow().status()).isEqualTo(HeatStatus.Started);
            assertThat(store.find(1, 2).orElseThrow().ended()).isNull();
            db.closeAndDelete();
        }
    }

    @Test
    void anEmptyHeatDoesNotBecomeThePreviousHeat() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            List<LiveHeats> seen = new ArrayList<>();
            service.register(seen::add);
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.start(1, 1, T0));
            service.accept(TestMessageCreator.end(1, 1, T0.plusMinutes(1)));
            service.accept(TestMessageCreator.used(1, 2, "1100000000"));

            service.accept(TestMessageCreator.start(1, 3, T0.plusMinutes(3)));

            assertThat(seen.get(seen.size() - 1).current().heat()).isEqualTo(3);
            assertThat(seen.get(seen.size() - 1).previous().heat()).isEqualTo(1);

            List<LiveHeats> restored = new ArrayList<>();
            new HeatService(store).register(restored::add);
            assertThat(restored.get(0).current().heat()).isEqualTo(3);
            assertThat(restored.get(0).previous().heat()).isEqualTo(1);
            db.closeAndDelete();
        }
    }

    @Test
    void anEstimatedEndDoesNotMakeTheEstimatedHeatTheCurrentHeatOfTheProject() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            service.accept(TestMessageCreator.start(1, 1, T0));
            service.accept(TestMessageCreator.start(1, 2, T0.plusMinutes(3)));

            List<LiveHeats> restored = new ArrayList<>();
            new HeatService(store).register(restored::add);

            assertThat(restored.get(0).current().heat()).isEqualTo(2);
            assertThat(restored.get(0).previous().heat()).isEqualTo(1);
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

    @Test
    void messagesWithoutEventOrHeatAndBadLanesAreIgnored() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            List<HeatDto> seen = new ArrayList<>();
            service.register(h -> seen.add(h.current()));
            service.accept(TestMessageCreator.used(0, 1, "1100000000"));
            service.accept(TestMessageCreator.used(1, 0, "1100000000"));
            assertThat(seen).isEmpty();
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.split(1, 1, 11, 1, 1000)); // lane 11 does not exist: logged, no change, no exception
            service.accept(TestMessageCreator.used(1, 1, "short")); // malformed: logged
            assertThat(seen).hasSize(1);
            db.closeAndDelete();
        }
    }

    @Test
    void heatsHaveTheTenLanesOfTheProtocol() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            service.accept(TestMessageCreator.used(1, 1, "0000000011"));
            service.accept(TestMessageCreator.split(1, 1, 9, 1, 1000));
            service.accept(TestMessageCreator.fin(1, 1, 10, 0, 2000));
            HeatDto heat = store.find(1, 1).orElseThrow();
            assertThat(heat.lanes()).extracting(LaneDto::number).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            assertThat(heat.lanes().get(8)).isEqualTo(new LaneDto(9, 1000, 1, LaneStatus.Used, Penalty.None, 0, false));
            assertThat(heat.lanes().get(9)).isEqualTo(new LaneDto(10, 2000, 2, LaneStatus.Used, Penalty.None, 1, true));
            db.closeAndDelete();
        }
    }

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
    void aTimeAfterADidNotStartKeepsThePenaltyAndGetsNoPlace() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.start(1, 1, T0));
            service.accept(TestMessageCreator.dns(1, 1, 1));
            service.accept(TestMessageCreator.fin(1, 1, 1, 0, 2000));
            service.accept(TestMessageCreator.fin(1, 1, 2, 0, 2100));
            HeatDto heat = store.find(1, 1).orElseThrow();
            assertThat(heat.lanes().get(0)).isEqualTo(new LaneDto(1, 2000, 2, LaneStatus.Used, Penalty.DidNotStart, 0, true));
            assertThat(heat.lanes().get(1).place()).isEqualTo(1);
            db.closeAndDelete();
        }
    }

    @Test
    void aSecondFinishForTheSameLaneReplacesTheTimeAndALaterSplitIsIgnored() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.start(1, 1, T0));
            service.accept(TestMessageCreator.fin(1, 1, 1, 0, 2000));
            service.accept(TestMessageCreator.fin(1, 1, 1, 0, 2050));
            assertThat(store.find(1, 1).orElseThrow().lanes().get(0)).isEqualTo(new LaneDto(1, 2050, 2, LaneStatus.Used, Penalty.None, 1, true));

            service.accept(TestMessageCreator.split(1, 1, 1, 1, 1000));
            assertThat(store.find(1, 1).orElseThrow().lanes().get(0)).isEqualTo(new LaneDto(1, 2050, 2, LaneStatus.Used, Penalty.None, 1, true));
            db.closeAndDelete();
        }
    }

    @Test
    void aRejectedMessageForAnUnknownHeatLeavesNoEmptyHeatBehind() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.split(1, 2, 11, 1, 1000));
            assertThat(store.find(1, 2)).isEmpty();
            assertThat(store.findLatest().orElseThrow().heat()).isEqualTo(1);
            db.closeAndDelete();
        }
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
    void repeatedResultsOfPreviousRacesDoNotReselectOrChangeOlderHeats() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            HeatStore store = new HeatStore(db);
            HeatService service = new HeatService(store);
            service.accept(TestMessageCreator.used(1, 1, "1100000000"));
            service.accept(TestMessageCreator.start(1, 1, T0));
            service.accept(TestMessageCreator.fin(1, 1, 1, 0, 1900));
            service.accept(TestMessageCreator.used(1, 2, "1100000000"));
            service.accept(TestMessageCreator.start(1, 2, T0.plusSeconds(60)));
            List<HeatDto> seen = new ArrayList<>();
            service.register(h -> seen.add(h.current()));
            assertThat(seen).hasSize(1);

            service.accept(TestMessageCreator.startPrevious(1, 1, T0.plusSeconds(90)));

            assertThat(seen).hasSize(1);
            assertThat(store.findLatest().orElseThrow().heat()).isEqualTo(2);
            assertThat(store.find(1, 1).orElseThrow().lanes().get(0).timeInMillis()).isEqualTo(1900);
            db.closeAndDelete();
        }
    }
}
