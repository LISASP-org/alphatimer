package org.lisasp.alphatimer.test.heats;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.refinedmessages.accepted.OfficialEndMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.StartMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.TimeMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.UsedLanesMessage;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;
import org.lisasp.alphatimer.heats.api.HeatDto;
import org.lisasp.alphatimer.heats.api.LaneDto;
import org.lisasp.alphatimer.heats.api.enums.HeatStatus;
import org.lisasp.alphatimer.heats.api.enums.LaneStatus;
import org.lisasp.alphatimer.heats.api.enums.Penalty;
import org.lisasp.alphatimer.heats.entity.HeatEntity;
import org.lisasp.alphatimer.heats.service.DataRepository;
import org.lisasp.alphatimer.heats.service.HeatRepository;
import org.lisasp.alphatimer.heats.service.HeatService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeatServiceTest {

    private static final LocalDateTime TIMESTAMP1 = LocalDateTime.of(2021, 6, 10, 10, 1);
    private static final LocalDateTime TIMESTAMP2 = LocalDateTime.of(2021, 6, 10, 10, 2);
    private static final LocalDateTime TIMESTAMP3 = LocalDateTime.of(2021, 6, 10, 10, 3);
    private static final LocalDateTime TIMESTAMP4 = LocalDateTime.of(2021, 6, 10, 10, 4);
    private static final LocalDateTime TIMESTAMP5 = LocalDateTime.of(2021, 6, 10, 10, 5);
    private static final LocalDateTime TIMESTAMP6 = LocalDateTime.of(2021, 6, 10, 10, 6);
    private static final LocalDateTime TIMESTAMP7 = LocalDateTime.of(2021, 6, 10, 10, 7);

    private static final int TIME_OF_DAY_MILLIS = 10 * 60 * 60 * 1000;

    private static final String COMPETITION = "TestWK";
    private static final short EVENT = 1;
    private static final byte HEAT = 2;

    private final StartMessage startMessageEvent0Heat1 = new StartMessage(TIMESTAMP1,
                                                                          COMPETITION,
                                                                          (short) 0,
                                                                          (byte) 1,
                                                                          RefinedMessageType.Live,
                                                                          (byte) 0,
                                                                          (byte) 1,
                                                                          TIME_OF_DAY_MILLIS,
                                                                          RefinedTimeType.Normal);
    private final StartMessage startMessageEvent1Heat0 = new StartMessage(TIMESTAMP1,
                                                                          COMPETITION,
                                                                          (short) 1,
                                                                          (byte) 0,
                                                                          RefinedMessageType.Live,
                                                                          (byte) 0,
                                                                          (byte) 1,
                                                                          TIME_OF_DAY_MILLIS,
                                                                          RefinedTimeType.Normal);

    private final StartMessage startMessage = new StartMessage(TIMESTAMP1,
                                                               COMPETITION,
                                                               EVENT,
                                                               HEAT,
                                                               RefinedMessageType.Live,
                                                               (byte) 1,
                                                               (byte) 1,
                                                               TIME_OF_DAY_MILLIS,
                                                               RefinedTimeType.Normal);
    private final UsedLanesMessage usedLanesMessage = new UsedLanesMessage(TIMESTAMP2,
                                                                           COMPETITION,
                                                                           EVENT,
                                                                           HEAT,
                                                                           new UsedLanes(new boolean[]{true, true, true, true, true, true, true, true, false, false}).toValue());
    private final UsedLanesMessage notUsedLanesMessage = new UsedLanesMessage(TIMESTAMP3,
                                                                              COMPETITION,
                                                                              EVENT,
                                                                              HEAT,
                                                                              new UsedLanes(new boolean[]{false, false, false, false, false, false, false, false, false, false}).toValue());
    private final TimeMessage timeLane1Message = new TimeMessage(TIMESTAMP3,
                                                                 COMPETITION,
                                                                 EVENT,
                                                                 HEAT,
                                                                 RefinedMessageType.Live,
                                                                 RefinedKindOfTime.Finish,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 111110,
                                                                 RefinedTimeType.Normal);
    private final TimeMessage timeLane2Message = new TimeMessage(TIMESTAMP3,
                                                                 COMPETITION,
                                                                 EVENT,
                                                                 HEAT,
                                                                 RefinedMessageType.Live,
                                                                 RefinedKindOfTime.Finish,
                                                                 (byte) 2,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 111120,
                                                                 RefinedTimeType.Normal);
    private final TimeMessage timeLane3Message = new TimeMessage(TIMESTAMP3,
                                                                 COMPETITION,
                                                                 EVENT,
                                                                 HEAT,
                                                                 RefinedMessageType.Live,
                                                                 RefinedKindOfTime.Finish,
                                                                 (byte) 3,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 111130,
                                                                 RefinedTimeType.Normal);
    private final TimeMessage timeLane4Message = new TimeMessage(TIMESTAMP3,
                                                                 COMPETITION,
                                                                 EVENT,
                                                                 HEAT,
                                                                 RefinedMessageType.Live,
                                                                 RefinedKindOfTime.Finish,
                                                                 (byte) 4,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 111140,
                                                                 RefinedTimeType.Normal);
    private final TimeMessage timeLane5Message = new TimeMessage(TIMESTAMP3,
                                                                 COMPETITION,
                                                                 EVENT,
                                                                 HEAT,
                                                                 RefinedMessageType.Live,
                                                                 RefinedKindOfTime.Finish,
                                                                 (byte) 5,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 111150,
                                                                 RefinedTimeType.Normal);
    private final TimeMessage timeLane6Message = new TimeMessage(TIMESTAMP3,
                                                                 COMPETITION,
                                                                 EVENT,
                                                                 HEAT,
                                                                 RefinedMessageType.Live,
                                                                 RefinedKindOfTime.Finish,
                                                                 (byte) 6,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 111160,
                                                                 RefinedTimeType.Normal);
    private final TimeMessage timeLane7Message = new TimeMessage(TIMESTAMP3,
                                                                 COMPETITION,
                                                                 EVENT,
                                                                 HEAT,
                                                                 RefinedMessageType.Live,
                                                                 RefinedKindOfTime.Finish,
                                                                 (byte) 7,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 111170,
                                                                 RefinedTimeType.Normal);
    private final TimeMessage timeLane8Message = new TimeMessage(TIMESTAMP3,
                                                                 COMPETITION,
                                                                 EVENT,
                                                                 HEAT,
                                                                 RefinedMessageType.Live,
                                                                 RefinedKindOfTime.Finish,
                                                                 (byte) 8,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 111180,
                                                                 RefinedTimeType.Normal);
    private final TimeMessage timeLane9Message = new TimeMessage(TIMESTAMP3,
                                                                 COMPETITION,
                                                                 EVENT,
                                                                 HEAT,
                                                                 RefinedMessageType.Live,
                                                                 RefinedKindOfTime.Finish,
                                                                 (byte) 9,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 (byte) 1,
                                                                 111190,
                                                                 RefinedTimeType.Normal);
    private final OfficialEndMessage officialEndMessage = new OfficialEndMessage(TIMESTAMP4,
                                                                                 COMPETITION,
                                                                                 EVENT,
                                                                                 HEAT,
                                                                                 (byte) 1);

    private HeatService heatsService;
    private HeatRepository heatEntities;

    private static LaneDto[] createLanes(LaneStatus status) {
        return new LaneDto[]{
                new LaneDto(1, 0, status, Penalty.None, 0),
                new LaneDto(2, 0, status, Penalty.None, 0),
                new LaneDto(3, 0, status, Penalty.None, 0),
                new LaneDto(4, 0, status, Penalty.None, 0),
                new LaneDto(5, 0, status, Penalty.None, 0),
                new LaneDto(6, 0, status, Penalty.None, 0),
                new LaneDto(7, 0, status, Penalty.None, 0),
                new LaneDto(8, 0, status, Penalty.None, 0)
        };
    }

    private static LaneDto[] createPartiallyStartedLanes(int upToLane) {
        LaneDto[] lanes = new LaneDto[8];
        for (int x = 0; x < upToLane; x++) {
            lanes[x] = new LaneDto(x + 1, 111110 + 10 * x, LaneStatus.Used, Penalty.None, 1);
        }
        for (int x = upToLane; x < lanes.length; x++) {
            lanes[x] = new LaneDto(x + 1, 0, LaneStatus.Used, Penalty.None, 0);
        }
        return lanes;
    }

    private final LaneDto[] emptyLanes = createLanes(LaneStatus.NotUsed);
    private final LaneDto[] emptyLanesWithLapCount = createLanes(LaneStatus.NotUsed);

    private final LaneDto[] usedLanes = createLanes(LaneStatus.Used);

    private final LaneDto[] lane1Lanes = createPartiallyStartedLanes(1);

    private final LaneDto[] completeLanes = new LaneDto[]{
            new LaneDto(1, 111110, LaneStatus.Used, Penalty.None, 1),
            new LaneDto(2, 111120, LaneStatus.Used, Penalty.None, 1),
            new LaneDto(3, 111130, LaneStatus.Used, Penalty.None, 1),
            new LaneDto(4, 111140, LaneStatus.Used, Penalty.None, 1),
            new LaneDto(5, 111150, LaneStatus.Used, Penalty.None, 1),
            new LaneDto(6, 111160, LaneStatus.Used, Penalty.None, 1),
            new LaneDto(7, 111170, LaneStatus.Used, Penalty.None, 1),
            new LaneDto(8, 111180, LaneStatus.Used, Penalty.None, 1)
    };

    private TestHeatListener listener;

    @BeforeEach
    void prepare() {
        heatEntities = new TestHeatRepository();
        heatsService = new HeatService(new DataRepository(heatEntities), () -> TIMESTAMP1);

        listener = new TestHeatListener();
        heatsService.register(listener);
    }

    @Test
    void startMessage() {
        heatsService.accept(startMessage);

        assertEquals(List.of(new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Started, TIMESTAMP1, 1, emptyLanes)), listener.received);

        Optional<HeatEntity> entity = heatEntities.findByCompetitionAndEventAndHeat(COMPETITION, EVENT, HEAT);
        assertTrue(entity.isPresent());
        assertEquals(8, entity.get().getLanes().size());
    }

    @Test
    void unusedTimeLane1Message() {
        heatsService.accept(timeLane1Message);

        assertEquals(List.of(new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Started, TIMESTAMP3, 1, emptyLanes)), listener.received);

        Optional<HeatEntity> entity = heatEntities.findByCompetitionAndEventAndHeat(COMPETITION, EVENT, HEAT);
        assertTrue(entity.isPresent());
        assertEquals(8, entity.get().getLanes().size());
    }

    @Test
    void timeLane1Message() {
        heatsService.accept(usedLanesMessage);
        heatsService.accept(timeLane1Message);

        assertEquals(List.of(new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Started, TIMESTAMP3, 1, lane1Lanes)), listener.received);

        Optional<HeatEntity> entity = heatEntities.findByCompetitionAndEventAndHeat(COMPETITION, EVENT, HEAT);
        assertTrue(entity.isPresent());
        assertEquals(8, entity.get().getLanes().size());
    }

    @Test
    void officialEndMessage() {
        heatsService.accept(officialEndMessage);

        assertEquals(List.of(new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Finished, TIMESTAMP4, 1, emptyLanes)), listener.received);

        Optional<HeatEntity> entity = heatEntities.findByCompetitionAndEventAndHeat(COMPETITION, EVENT, HEAT);
        assertTrue(entity.isPresent());
        assertEquals(8, entity.get().getLanes().size());
    }

    @Test
    void emptyHeat() {
        heatsService.accept(startMessage);
        heatsService.accept(officialEndMessage);

        assertEquals(List.of(new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Started, TIMESTAMP1, 1, emptyLanesWithLapCount),
                              new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Finished, TIMESTAMP1, 1, emptyLanesWithLapCount)), listener.received);

        Optional<HeatEntity> entity = heatEntities.findByCompetitionAndEventAndHeat(COMPETITION, EVENT, HEAT);
        assertTrue(entity.isPresent());
        assertEquals(8, entity.get().getLanes().size());
    }

    @Test
    void unusedLanes() {
        heatsService.accept(startMessage);
        heatsService.accept(usedLanesMessage);
        heatsService.accept(notUsedLanesMessage);

        LaneDto[] lanes = createLanes(LaneStatus.NotUsed);
        lanes[0] = new LaneDto(1, 0, LaneStatus.Used, Penalty.None, 0);

        assertEquals(List.of(new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Started, TIMESTAMP1, 1, emptyLanesWithLapCount),
                              new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Started, TIMESTAMP1, 1, usedLanes),
                              new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Started, TIMESTAMP1, 1, emptyLanesWithLapCount)), listener.received);

        Optional<HeatEntity> entity = heatEntities.findByCompetitionAndEventAndHeat(COMPETITION, EVENT, HEAT);
        assertTrue(entity.isPresent());
        assertEquals(8, entity.get().getLanes().size());

    }

    @Test
    void completeHeat() {
        heatsService.accept(startMessage);
        heatsService.accept(usedLanesMessage);
        heatsService.accept(timeLane1Message);
        heatsService.accept(timeLane2Message);
        heatsService.accept(timeLane3Message);
        heatsService.accept(timeLane4Message);
        heatsService.accept(timeLane5Message);
        heatsService.accept(timeLane6Message);
        heatsService.accept(timeLane7Message);
        heatsService.accept(timeLane8Message);
        heatsService.accept(officialEndMessage);

        List<HeatDto> expected = new ArrayList<>();
        expected.add(new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Started, TIMESTAMP1, 1, emptyLanesWithLapCount));
        expected.add(new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Started, TIMESTAMP1, 1, usedLanes));
        for (int x = 1; x <= 8; x++) {
            expected.add(new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Started, TIMESTAMP1, 1, createPartiallyStartedLanes(x)));
        }
        expected.add(new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Finished, TIMESTAMP1, 1, completeLanes));

        assertEquals(expected, listener.received);

        Optional<HeatEntity> entity = heatEntities.findByCompetitionAndEventAndHeat(COMPETITION, EVENT, HEAT);
        assertTrue(entity.isPresent());
        assertEquals(8, entity.get().getLanes().size());

    }

    @Test
    void invalidEventNumber() {
        heatsService.accept(startMessageEvent0Heat1);

        assertEquals(List.of(), listener.received);

        assertEquals(0, heatEntities.count());
    }

    @Test
    void invalidHeatNumber() {
        heatsService.accept(startMessageEvent1Heat0);

        assertEquals(List.of(), listener.received);

        assertEquals(0, heatEntities.count());
    }

    @Test
    void laneDoesNotExist() {
        heatsService.accept(startMessage);
        Assertions.assertThrows(NoSuchElementException.class, () -> heatsService.accept(timeLane9Message), "TestWK 1 2: Lane 9 not found.");

        assertEquals(List.of(new HeatDto(COMPETITION, EVENT, HEAT, HeatStatus.Started, TIMESTAMP1, 1, emptyLanesWithLapCount)), listener.received);

        Optional<HeatEntity> entity = heatEntities.findByCompetitionAndEventAndHeat(COMPETITION, EVENT, HEAT);
        assertTrue(entity.isPresent());
        assertEquals(8, entity.get().getLanes().size());
    }
}
