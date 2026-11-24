package org.lisasp.alphatiming.heats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

public final class InMemoryHeatRepository implements HeatRepository {

    private record Key(int event, String round, int heat) {
    }

    private final Map<Key, HeatDto> heats = new LinkedHashMap<>();

    @Override
    public synchronized Optional<HeatDto> find(int event, String round, int heat) {
        return Optional.ofNullable(heats.get(new Key(event, round, heat)));
    }

    @Override
    public synchronized SortedSet<Integer> findUsedLanes() {
        SortedSet<Integer> used = new TreeSet<>();
        for (HeatDto heat : heats.values()) {
            for (LaneDto lane : heat.lanes()) {
                if (lane.status() == LaneStatus.Used) {
                    used.add(lane.number());
                }
            }
        }
        return used;
    }

    @Override
    public synchronized List<HeatDto> findAll() {
        List<HeatDto> all = new ArrayList<>(heats.values());
        Collections.reverse(all);
        return all;
    }

    @Override
    public synchronized HeatDto create(HeatDto dto) {
        return put(dto);
    }

    @Override
    public synchronized void save(HeatDto dto) {
        if (!heats.containsKey(new Key(dto.event(), dto.round(), dto.heat()))) {
            throw new NoSuchElementException("No heat " + dto.heat() + " in event " + dto.event());
        }
        put(dto);
    }

    private HeatDto put(HeatDto dto) {
        Key key = new Key(dto.event(), dto.round(), dto.heat());
        heats.remove(key);
        heats.put(key, dto);
        return dto;
    }

    @Override
    public synchronized void delete(int event, String round, int heat) {
        heats.remove(new Key(event, round, heat));
    }
}
