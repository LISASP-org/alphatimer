package org.lisasp.alphatimer.messagesstorage;

import org.lisasp.alphatimer.api.protocol.DataHandlingMessageRepository;
import org.lisasp.alphatimer.api.protocol.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.protocol.events.messages.values.UsedLanes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class Messages implements DataHandlingMessageRepository {

    private final AresMessageRepository repository;

    @Override
    public void put(DataHandlingMessage message, String competitionKey) {
        repository.save(mapDataHandlingMessageToAresMessage(message, competitionKey));
    }

    @Override
    public List<DataHandlingMessage> findBy(String competitionKey, short event, byte heat) {
        return repository.findAllByCompetitionKeyAndEventAndHeat(competitionKey, event, heat)
                         .stream()
                         .map(am -> mapAresMessageToDataHandlingMessage(am))
                         .collect(Collectors.toList());
    }

    @Override
    public int size() {
        return (int) repository.count();
    }

    private AresMessage mapDataHandlingMessageToAresMessage(DataHandlingMessage am, String competitionKey) {
        return new AresMessage(am.getOriginalText1(),
                               am.getOriginalText2(),
                               am.getMessageType(),
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
                               am.getTimeMarker(),
                               competitionKey);
    }

    private DataHandlingMessage mapAresMessageToDataHandlingMessage(AresMessage am) {
        return new DataHandlingMessage(am.getOriginalText1(),
                                       am.getOriginalText2(),
                                       am.getMessageType(),
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

    private UsedLanes toUsedLanes(String usedLanes) {
        boolean[] bitSet = new boolean[10];

        for (int x = 0; x < usedLanes.length(); x++) {
            bitSet[x] = usedLanes.toCharArray()[x] == '+';
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