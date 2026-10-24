package org.lisasp.alphatiming.project.db;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatRepository;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Optional;

@RequiredArgsConstructor
public final class HeatStore implements HeatRepository {

    private final ProjectDatabase db;

    @Override
    public Optional<HeatDto> find(String competition, int event, int heat) {
        return db.read(em -> findEntity(em, competition, event, heat).map(HeatStore::toDto));
    }

    public Optional<HeatDto> findLatest() {
        return findRecent(1).stream().findFirst();
    }

    /** Every lane number that is used in at least one heat of the project. */
    @Override
    public SortedSet<Integer> findUsedLanes() {
        return db.read(em -> new TreeSet<>(em.createQuery("select distinct l.number from LaneEntity l where l.status = :used", Integer.class)
                                              .setParameter("used", LaneStatus.Used)
                                              .getResultList()));
    }

    /** The most recently changed heats, newest first. */
    @Override
    public List<HeatDto> findRecent(int count) {
        return db.read(em -> em.createQuery("select h from HeatEntity h order by h.changeSeq desc", HeatEntity.class)
                               .setMaxResults(count)
                               .getResultStream()
                               .map(HeatStore::toDto)
                               .toList());
    }

    public HeatDto create(String competition, int event, int heat, int laneCount) {
        return create(HeatDto.empty(competition, event, heat, laneCount));
    }

    @Override
    public HeatDto create(HeatDto dto) {
        return db.write(em -> {
            HeatEntity e = new HeatEntity();
            e.setCompetition(dto.competition());
            e.setEvent(dto.event());
            e.setHeatNumber(dto.heat());
            apply(em, e, dto);
            em.persist(e);
            return toDto(e);
        });
    }

    @Override
    public void save(HeatDto dto) {
        db.write(em -> {
            apply(em, findEntity(em, dto.competition(), dto.event(), dto.heat()).orElseThrow(), dto);
            return null;
        });
    }

    private static void apply(EntityManager em, HeatEntity e, HeatDto dto) {
        e.setStatus(dto.status());
        e.setStarted(dto.started());
        e.setEnded(dto.ended());
        e.setLastMessage(dto.lastMessage());
        e.setLapCount(dto.lapCount());
        e.setChangeSeq(nextSeq(em));
        e.getLanes().removeIf(l -> dto.lanes().stream().noneMatch(d -> d.number() == l.getNumber()));
        for (LaneDto d : dto.lanes()) {
            LaneEntity lane = e.getLanes().stream().filter(l -> l.getNumber() == d.number()).findFirst().orElseGet(() -> {
                LaneEntity created = new LaneEntity();
                created.setHeat(e);
                created.setNumber(d.number());
                e.getLanes().add(created);
                return created;
            });
            lane.setTimeInMillis(d.timeInMillis());
            lane.setLap(d.lap());
            lane.setStatus(d.status());
            lane.setPenalty(d.penalty());
            lane.setPlace(d.place());
            lane.setFinished(d.finished());
        }
    }

    private static long nextSeq(EntityManager em) {
        Long max = em.createQuery("select max(h.changeSeq) from HeatEntity h", Long.class).getSingleResult();
        return max == null ? 1 : max + 1;
    }

    private static Optional<HeatEntity> findEntity(EntityManager em, String competition, int event, int heat) {
        return em.createQuery("select h from HeatEntity h where h.competition = :c and h.event = :e and h.heatNumber = :h", HeatEntity.class)
                 .setParameter("c", competition)
                 .setParameter("e", event)
                 .setParameter("h", heat)
                 .getResultStream()
                 .findFirst();
    }

    private static HeatDto toDto(HeatEntity e) {
        return new HeatDto(e.getCompetition(),
                           e.getEvent(),
                           e.getHeatNumber(),
                           e.getStatus(),
                           e.getStarted(),
                           e.getEnded(),
                           e.getLapCount(),
                           e.getLanes()
                            .stream()
                            .sorted(java.util.Comparator.comparingInt(LaneEntity::getNumber))
                            .map(l -> new LaneDto(l.getNumber(), l.getTimeInMillis(), l.getLap(), l.getStatus(), l.getPenalty(), l.getPlace(), l.isFinished()))
                            .toList(),
                           e.getLastMessage());
    }
}
