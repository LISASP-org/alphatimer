package org.lisasp.alphatiming.heats;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.lisasp.alphatiming.heats.TestMessageCreator.T0;

class HeatServiceTest {

    @Test
    void sameHeatNumberInDifferentEventsStaysSeparate() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
        HeatService service = new HeatService(store);
        service.accept(TestMessageCreator.used(1, 1, "1100000000"));
        service.accept(TestMessageCreator.used(2, 1, "0011000000"));
        service.accept(TestMessageCreator.split(1, 1, 1, 1, 1000));
        assertThat(store.find(1, 1).orElseThrow().lanes().get(0).current().orElseThrow().timeInMillis()).isEqualTo(1000);
        assertThat(store.find(2, 1).orElseThrow().lanes().get(0).status()).isEqualTo(LaneStatus.NotUsed);
    }

    @Test
    void theTimeOfDayOfTheStartIsStoredWithTheHeat() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
        new HeatService(store).accept(TestMessageCreator.startAtTimeOfDay(1, 1, T0, 36_000_000));
        assertThat(store.find(1, 1).orElseThrow().startTimeOfDayMillis()).isEqualTo(36_000_000);
    }

    @Test
    void listenersGetCurrentHeatOnRegistrationAndChangesAfterwards() {
        HeatService service = new HeatService(new InMemoryHeatRepository());
        service.accept(TestMessageCreator.used(1, 1, "1100000000"));
        List<HeatDto> seen = new ArrayList<>();
        service.register(h -> seen.add(h.current()));
        assertThat(seen).hasSize(1);
        service.accept(TestMessageCreator.start(1, 1, T0));
        assertThat(seen).hasSize(2);
        assertThat(seen.get(1).status()).isEqualTo(HeatStatus.Started);
        service.accept(TestMessageCreator.start(1, 1, T0)); // no change
        assertThat(seen).hasSize(2);
    }

    @Test
    void theHeatThatWasCurrentBeforeBecomesThePreviousHeatWithItsLastState() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
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
        assertThat(afterSwitch.previous().lanes().get(0).current().orElseThrow().timeInMillis()).isEqualTo(1900);
        assertThat(afterSwitch.previous().lanes().get(0).place()).isEqualTo(1);

        service.accept(TestMessageCreator.start(1, 2, T0.plusSeconds(60)));
        service.accept(TestMessageCreator.split(1, 2, 1, 1, 800));
        LiveHeats during = seen.get(seen.size() - 1);
        assertThat(during.current().heat()).isEqualTo(2);
        assertThat(during.previous().heat()).isEqualTo(1);

        service.accept(TestMessageCreator.used(1, 3, "1100000000"));
        assertThat(seen.get(seen.size() - 1).previous().heat()).isEqualTo(2);
    }

    @Test
    void aNewServiceStartsWithTheCurrentAndPreviousHeatOfTheProject() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
        HeatService first = new HeatService(store);
        first.accept(TestMessageCreator.start(1, 1, T0));
        first.accept(TestMessageCreator.used(1, 2, "1100000000"));

        HeatService second = new HeatService(store);
        List<LiveHeats> seen = new ArrayList<>();
        second.register(seen::add);

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).current().heat()).isEqualTo(2);
        assertThat(seen.get(0).previous().heat()).isEqualTo(1);
    }

    @Test
    void lanesThatWereUsedOnceStayKnownForTheLiveView() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
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
    }

    @Test
    void everyHeatOfTheProjectIsKnownWithItsLatestState() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
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
    }

    @Test
    void aStartedHeatWithoutEndIsEstimatedFinishedByTheFirstMessageOfAnotherHeat() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
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
    }

    @Test
    void aHeatThatFellSilentLongBeforeTheNextHeatIsEstimatedToEndWhenItsClockStopped() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
        HeatService service = new HeatService(store);
        service.accept(TestMessageCreator.start(1, 1, T0));

        service.accept(TestMessageCreator.start(1, 2, T0.plusMinutes(30)));

        assertThat(store.find(1, 1).orElseThrow().ended()).isEqualTo(T0.plus(RunningClock.STALE_AFTER));
    }

    @Test
    void onlyAStartedHeatWithoutEndIsEstimated() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
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
    }

    @Test
    void aMessageOfAnotherHeatThatChangesNothingEstimatesNothing() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
        HeatService service = new HeatService(store);
        service.accept(TestMessageCreator.used(1, 1, "1100000000"));
        service.accept(TestMessageCreator.start(1, 2, T0));

        service.accept(TestMessageCreator.used(1, 1, "1100000000"));

        assertThat(store.find(1, 2).orElseThrow().status()).isEqualTo(HeatStatus.Started);
        assertThat(store.find(1, 2).orElseThrow().ended()).isNull();
    }

    @Test
    void anEmptyHeatDoesNotBecomeThePreviousHeat() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
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
    }

    @Test
    void anEstimatedEndDoesNotMakeTheEstimatedHeatTheCurrentHeatOfTheProject() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
        HeatService service = new HeatService(store);
        service.accept(TestMessageCreator.start(1, 1, T0));
        service.accept(TestMessageCreator.start(1, 2, T0.plusMinutes(3)));

        List<LiveHeats> restored = new ArrayList<>();
        new HeatService(store).register(restored::add);

        assertThat(restored.get(0).current().heat()).isEqualTo(2);
        assertThat(restored.get(0).previous().heat()).isEqualTo(1);
    }

    @Test
    void messagesWithoutEventOrHeatAndBadLanesAreIgnored() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
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
    }

    @Test
    void heatsHaveTheTenLanesOfTheProtocol() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
        HeatService service = new HeatService(store);
        service.accept(TestMessageCreator.used(1, 1, "0000000011"));
        service.accept(TestMessageCreator.split(1, 1, 9, 1, 1000));
        service.accept(TestMessageCreator.fin(1, 1, 10, 0, 2000));
        HeatDto heat = store.find(1, 1).orElseThrow();
        assertThat(heat.lanes()).extracting(LaneDto::number).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(heat.lanes().get(8)).isEqualTo(TestLanes.lane(9, LaneStatus.Used, Set.of(), 0, TestLanes.split(1, 1000)));
        assertThat(heat.lanes().get(9)).isEqualTo(TestLanes.lane(10, LaneStatus.Used, Set.of(), 1, TestLanes.finish(2, 2000)));
    }

    @Test
    void aTimeAfterADidNotStartKeepsThePenaltyAndGetsNoPlace() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
        HeatService service = new HeatService(store);
        service.accept(TestMessageCreator.used(1, 1, "1100000000"));
        service.accept(TestMessageCreator.start(1, 1, T0));
        service.accept(TestMessageCreator.dns(1, 1, 1));
        service.accept(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        service.accept(TestMessageCreator.fin(1, 1, 2, 0, 2100));
        HeatDto heat = store.find(1, 1).orElseThrow();
        assertThat(heat.lanes().get(0)).isEqualTo(TestLanes.lane(1, LaneStatus.Used, Set.of(Penalty.DidNotStart), 0, TestLanes.finish(2, 2000)));
        assertThat(heat.lanes().get(1).place()).isEqualTo(1);
    }

    @Test
    void aDisqualificationIsStoredWithTheTimeOfTheLane() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
        HeatService service = new HeatService(store);
        service.accept(TestMessageCreator.used(1, 1, "1100000000"));
        service.accept(TestMessageCreator.start(1, 1, T0));
        service.accept(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        service.accept(TestMessageCreator.fin(1, 1, 2, 0, 2100));
        service.accept(TestMessageCreator.dsq(1, 1, 1));
        HeatDto heat = store.find(1, 1).orElseThrow();
        assertThat(heat.lanes().get(0)).isEqualTo(TestLanes.lane(1, LaneStatus.Used, Set.of(Penalty.Disqualified), 0, TestLanes.finish(2, 2000)));
        assertThat(heat.lanes().get(1).place()).isEqualTo(1);
    }

    @Test
    void aSecondFinishForTheSameLaneSupersedesTheTimeAndALaterSplitDoesNotReplaceIt() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
        HeatService service = new HeatService(store);
        service.accept(TestMessageCreator.used(1, 1, "1100000000"));
        service.accept(TestMessageCreator.start(1, 1, T0));
        service.accept(TestMessageCreator.fin(1, 1, 1, 0, 2000));
        service.accept(TestMessageCreator.fin(1, 1, 1, 0, 2050));
        LaneDto lane = store.find(1, 1).orElseThrow().lanes().get(0);
        assertThat(lane.current().orElseThrow()).isEqualTo(TestLanes.finish(2, 2050));
        assertThat(lane.place()).isEqualTo(1);

        service.accept(TestMessageCreator.split(1, 1, 1, 1, 1000));
        lane = store.find(1, 1).orElseThrow().lanes().get(0);
        assertThat(lane.current().orElseThrow()).isEqualTo(TestLanes.finish(2, 2050));
        assertThat(lane.place()).isEqualTo(1);
    }

    @Test
    void aRejectedMessageForAnUnknownHeatLeavesNoEmptyHeatBehind() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
        HeatService service = new HeatService(store);
        service.accept(TestMessageCreator.used(1, 1, "1100000000"));
        service.accept(TestMessageCreator.split(1, 2, 11, 1, 1000));
        assertThat(store.find(1, 2)).isEmpty();
        assertThat(store.findAll().get(0).heat()).isEqualTo(1);
    }

    @Test
    void repeatedResultsOfPreviousRacesDoNotReselectOrChangeOlderHeats() {
        InMemoryHeatRepository store = new InMemoryHeatRepository();
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
        assertThat(store.findAll().get(0).heat()).isEqualTo(2);
        assertThat(store.find(1, 1).orElseThrow().lanes().get(0).current().orElseThrow().timeInMillis()).isEqualTo(1900);
    }
}
