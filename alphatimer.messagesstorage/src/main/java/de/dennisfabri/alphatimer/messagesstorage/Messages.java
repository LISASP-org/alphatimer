package de.dennisfabri.alphatimer.messagesstorage;

import de.dennisfabri.alphatimer.api.events.messages.DataHandlingMessage;
import de.dennisfabri.alphatimer.api.events.messages.values.UsedLanes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class Messages {

    private final AresMessageRepository repository;

    public void put(DataHandlingMessage message) {
        repository.save(mapDataHandlingMessageToAresMessage(message));
    }

    public List<DataHandlingMessage> get(short event, byte heat) {
        return repository.findAllByEventAndHeat(event, heat)
                .stream()
                .map(am -> mapAresMessageToDataHandlingMessage(am))
                .collect(Collectors.toList());
    }

    private AresMessage mapDataHandlingMessageToAresMessage(DataHandlingMessage am) {
        return new AresMessage(am.getMessageType(),
                               am.getKindOfTime(),
                               am.getTimeType(),
                               toString(am.getUsedLanes()),
                               am.getLapCount(),
                               am.getEvent(),
                               am.getHeat(),
                               am.getRank(),
                               am.getRankInfo(),
                               am.getLane(),
                               am.getCurrentLap(),
                               am.getTimeInMillis(),
                               am.getTimeInfo(),
                               am.getTimeMarker());
    }

    private DataHandlingMessage mapAresMessageToDataHandlingMessage(AresMessage am) {
        return new DataHandlingMessage(am.getMessageType(),
                                       am.getKindOfTime(),
                                       am.getTimeType(),
                                       toUsedLanes(am.getUsedLanes()),
                                       am.getLapCount(),
                                       am.getEvent(),
                                       am.getHeat(),
                                       am.getRank(),
                                       am.getRankInfo(),
                                       am.getLane(),
                                       am.getCurrentLap(),
                                       am.getTimeInMillis(),
                                       am.getTimeInfo(),
                                       am.getTimeMarker());
    }

    public int size() {
        return (int) repository.count();
    }

    private UsedLanes toUsedLanes(String usedLanes) {
        BitSet bitSet = new BitSet();

        for (int x = 0; x < usedLanes.length(); x++) {
            bitSet.set(x, usedLanes.toCharArray()[x] == '+');
        }
        return new UsedLanes(bitSet);
    }

    private String toString(UsedLanes usedLanes) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < 10; x++) {
            sb.append(usedLanes.isUsed(x) ? "+" : "-");
        }
        return sb.toString();
    }
}
