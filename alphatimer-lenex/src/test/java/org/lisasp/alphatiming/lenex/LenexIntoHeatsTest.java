package org.lisasp.alphatiming.lenex;

import org.lisasp.alphatiming.lenex.api.LenexMeet;
import org.lisasp.alphatiming.lenex.api.LenexEvent;
import org.lisasp.alphatiming.lenex.api.LenexHeat;
import org.lisasp.alphatiming.lenex.api.LenexResult;
import org.lisasp.alphatiming.lenex.api.LenexSplit;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatService;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.InMemoryHeatRepository;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.Penalty;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.LaneNumbering;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class LenexIntoHeatsTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 1, 10, 10, 0);
    private static final LenexMeet EMPTY = new LenexMeet(OptionalInt.empty(), List.of());

    private final InMemoryHeatRepository store = new InMemoryHeatRepository();
    private final List<RuntimeException> errors = new ArrayList<>();
    private final HeatService heats = new HeatService(store, errors::add);
    private final LenexRefiner refiner = new LenexRefiner("");

    @Test
    void anOfficialHeatGetsItsTimesPlacesAndPenalties() {
        apply(EMPTY, official(result(4, 62340, "", new LenexSplit(50, 30120)), result(5, 61000, ""), result(6, 63000, "DSQ")));

        HeatDto heat = store.find(3, "TimedFinal", 2).orElseThrow();
        assertThat(errors).isEmpty();
        assertThat(heat.status()).isEqualTo(HeatStatus.Finished);
        assertThat(lane(heat, 5).place()).isEqualTo(1);
        assertThat(lane(heat, 4).place()).isEqualTo(2);
        assertThat(lane(heat, 4).times()).extracting(t -> t.lap() + "/" + t.timeInMillis()).containsExactly("1/30120", "2/62340");
        assertThat(lane(heat, 6).penalties()).containsExactly(Penalty.Disqualified);
        assertThat(lane(heat, 6).place()).isZero();
    }

    @Test
    void aResultThatDisappearedLeavesItsLaneUnused() {
        LenexMeet before = official(result(4, 62340, ""), result(5, 61000, ""));
        apply(EMPTY, before);

        apply(before, official(result(4, 62340, "")));

        HeatDto heat = store.find(3, "TimedFinal", 2).orElseThrow();
        assertThat(lane(heat, 5).status()).isEqualTo(LaneStatus.NotUsed);
        assertThat(lane(heat, 4).place()).isEqualTo(1);
    }

    @Test
    void laneZeroIsAnOrdinaryLane() {
        apply(EMPTY, official(result(0, 62340, ""), result(1, 61000, "")));

        HeatDto heat = store.find(3, "TimedFinal", 2).orElseThrow();
        assertThat(errors).isEmpty();
        assertThat(lane(heat, 0).place()).isEqualTo(2);
        assertThat(heats.liveHeats().laneNumbering()).isEqualTo(LaneNumbering.ZeroBased);
        assertThat(heats.liveHeats().usedLanes()).containsExactly(0, 1);
    }

    private void apply(LenexMeet previous, LenexMeet next) {
        refiner.refine(previous, next, AT).forEach(heats::accept);
    }

    private static LenexMeet official(LenexResult... results) {
        return new LenexMeet(OptionalInt.empty(), List.of(new LenexEvent(3, "", "LCM", 100, 1, List.of(new LenexHeat(2, "OFFICIAL", List.of(results))))));
    }

    private static LenexResult result(int lane, int millis, String status, LenexSplit... splits) {
        return new LenexResult(lane, OptionalInt.of(millis), status, List.of(splits));
    }

    private static LaneDto lane(HeatDto heat, int number) {
        return heat.lanes().stream().filter(l -> l.number() == number).findFirst().orElseThrow();
    }
}
