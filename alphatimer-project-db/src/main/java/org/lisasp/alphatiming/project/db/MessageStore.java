package org.lisasp.alphatiming.project.db;

import lombok.RequiredArgsConstructor;
import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageRepository;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;

import java.util.List;

@RequiredArgsConstructor
public class MessageStore implements DataHandlingMessageRepository {

    private final TimingDatabase db;

    @Override
    public void put(DataHandlingMessage m) {
        AresMessageEntity e = new AresMessageEntity();
        e.setOriginalText1(m.getOriginalText1());
        e.setOriginalText2(m.getOriginalText2());
        e.setTimestamp(m.getTimestamp());
        e.setMessageType(m.getMessageType());
        e.setKindOfTime(m.getKindOfTime());
        e.setTimeType(m.getTimeType());
        e.setUsedLanes(toString(m.getUsedLanes()));
        e.setLapCount(m.getLapCount());
        e.setEvent(m.getEvent());
        e.setHeat(m.getHeat());
        e.setRank(m.getRank());
        e.setRankInfo(m.getRankInfo());
        e.setLane(m.getLane());
        e.setCurrentLap(m.getCurrentLap());
        e.setTimeInMillis(m.getTimeInMillis());
        e.setTimeInfo(m.getTimeInfo());
        e.setTimeMarker(m.getTimeMarker());
        e.setCompetitionKey(m.getCompetition());
        db.write(em -> {
            em.persist(e);
            return null;
        });
    }

    @Override
    public List<DataHandlingMessage> findBy(String competition, short event, byte heat) {
        return db.read(em -> em.createQuery("select a from AresMessageEntity a where a.competitionKey = :c and a.event = :e and a.heat = :h order by a.id",
                                            AresMessageEntity.class)
                               .setParameter("c", competition)
                               .setParameter("e", event)
                               .setParameter("h", heat)
                               .getResultList()
                               .stream()
                               .map(MessageStore::toMessage)
                               .toList());
    }

    private static DataHandlingMessage toMessage(AresMessageEntity e) {
        return new DataHandlingMessage(e.getTimestamp(),
                                       e.getCompetitionKey(),
                                       e.getOriginalText1(),
                                       e.getOriginalText2(),
                                       e.getMessageType(),
                                       e.getKindOfTime(),
                                       e.getTimeType(),
                                       toUsedLanes(e.getUsedLanes()),
                                       e.getLapCount(),
                                       e.getEvent(),
                                       e.getHeat(),
                                       e.getRank(),
                                       e.getRankInfo(),
                                       e.getLane(),
                                       e.getCurrentLap(),
                                       e.getTimeInMillis(),
                                       e.getTimeInfo(),
                                       e.getTimeMarker());
    }

    private static UsedLanes toUsedLanes(String usedLanes) {
        boolean[] bitSet = new boolean[10];
        for (int x = 0; x < Math.min(usedLanes.length(), bitSet.length); x++) {
            bitSet[x] = usedLanes.charAt(x) == '+';
        }
        return new UsedLanes(bitSet);
    }

    private static String toString(UsedLanes usedLanes) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < 10; x++) {
            sb.append(usedLanes.isUsed(x) ? "+" : "-");
        }
        return sb.toString();
    }
}
