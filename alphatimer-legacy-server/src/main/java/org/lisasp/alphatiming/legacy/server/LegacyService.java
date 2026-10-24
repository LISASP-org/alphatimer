package org.lisasp.alphatiming.legacy.server;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageListener;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatiming.legacy.server.dto.Heat;
import org.lisasp.alphatiming.legacy.server.dto.LaneStatus;

import java.util.HashMap;
import java.util.List;

/** Keeps the finish times in the format of the former AlphaServer so existing consumers can still fetch them. */
@Slf4j
public final class LegacyService implements DataHandlingMessageListener {

    private final LaneTimeRepository store;

    public LegacyService(LaneTimeRepository store) {
        this.store = store;
    }

    @Override
    public void accept(DataHandlingMessage message) {
        if (message.getMessageType() == MessageType.OfficialEnd) {
            return;
        }
        if (message.getKindOfTime() != KindOfTime.Finish) {
            return;
        }
        if (message.getEvent() <= 0 || message.getHeat() <= 0) {
            return;
        }

        LaneStatus status = switch (message.getMessageType()) {
            case OnLineTime -> LaneStatus.RaceTimes;
            case CurrentRaceResults -> LaneStatus.ResultsOfTheRace;
            case CurrentRaceResultsWithBackupTimes -> LaneStatus.ResultsWithBackupTimes;
            case PreviousRaceResults -> LaneStatus.ResultsOfThePreviousRace;
            case PreviousRaceResultsWithBackupTimes -> LaneStatus.BackupOfThePreviousRace;
            default -> LaneStatus.NotUsed;
        };

        store.save(new LaneTime(message.getCompetition(),
                                message.getEvent(),
                                message.getHeat(),
                                message.getLane() - 1,
                                message.getTimeInMillis(),
                                status,
                                message.getTimestamp()));
    }

    public Heat[] getHeats() {
        HashMap<String, Heat> heats = new HashMap<>();
        List<LaneTime> laneTimes = store.findAll().stream().sorted().toList();
        for (LaneTime laneTime : laneTimes) {
            Heat heat = heats.computeIfAbsent(Heat.computeKey(laneTime.event(), laneTime.heatNumber()),
                                              key -> new Heat("" + (heats.size() + 1), laneTime.event(), laneTime.heatNumber()));
            heat.store(laneTime.laneIndex(), laneTime.timeInMillis(), laneTime.status());
        }
        return heats.values().stream().sorted().toArray(Heat[]::new);
    }
}
