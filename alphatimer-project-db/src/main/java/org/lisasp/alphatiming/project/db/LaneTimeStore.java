package org.lisasp.alphatiming.project.db;

import lombok.RequiredArgsConstructor;
import org.lisasp.alphatiming.legacy.server.LaneTime;
import org.lisasp.alphatiming.legacy.server.LaneTimeRepository;

import java.util.List;

@RequiredArgsConstructor
public class LaneTimeStore implements LaneTimeRepository {

    private final TimingDatabase db;

    @Override
    public void save(LaneTime laneTime) {
        LaneTimeEntity entity = new LaneTimeEntity();
        entity.setCompetition(laneTime.competition());
        entity.setEvent(laneTime.event());
        entity.setHeatNumber(laneTime.heatNumber());
        entity.setLaneIndex(laneTime.laneIndex());
        entity.setTimeInMillis(laneTime.timeInMillis());
        entity.setStatus(laneTime.status());
        entity.setTimestamp(laneTime.timestamp());
        db.write(em -> {
            em.persist(entity);
            return null;
        });
    }

    @Override
    public List<LaneTime> findAll() {
        return db.read(em -> em.createQuery("select l from LaneTimeEntity l", LaneTimeEntity.class)
                               .getResultStream()
                               .map(LaneTimeStore::toLaneTime)
                               .toList());
    }

    @Override
    public void delete(int event, int heatNumber, int laneIndex, long timeInMillis) {
        db.write(em -> em.createQuery("delete from LaneTimeEntity l where l.event = :event and l.heatNumber = :heatNumber"
                                              + " and l.laneIndex = :laneIndex and l.timeInMillis = :timeInMillis")
                         .setParameter("event", event)
                         .setParameter("heatNumber", heatNumber)
                         .setParameter("laneIndex", laneIndex)
                         .setParameter("timeInMillis", timeInMillis)
                         .executeUpdate());
    }

    private static LaneTime toLaneTime(LaneTimeEntity e) {
        return new LaneTime(e.getCompetition(), e.getEvent(), e.getHeatNumber(), e.getLaneIndex(), e.getTimeInMillis(), e.getStatus(), e.getTimestamp());
    }
}
