package org.lisasp.alphatiming.heats;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class HeatServiceListenerTest {

    @Test
    void aFailingListenerNeitherStopsTheOthersNorCountsAsAProcessingError() {
        InMemoryHeats store = new InMemoryHeats();
        List<RuntimeException> errors = new ArrayList<>();
        HeatService service = new HeatService(store, errors::add);
        List<LiveHeats> seen = new ArrayList<>();
        service.register(h -> {
            throw new IllegalStateException("listener broken");
        });
        service.register(seen::add);

        service.accept(TestMessageCreator.used(1, 1, "1100000000"));

        assertThat(store.find(1, 1)).isPresent();
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).current()).isEqualTo(store.find(1, 1).orElseThrow());
        assertThat(errors).isEmpty();
    }

    @Test
    void aStoreFailureIsReportedAndNotAnnounced() {
        InMemoryHeats store = new InMemoryHeats();
        store.failing = true;
        List<RuntimeException> errors = new ArrayList<>();
        HeatService service = new HeatService(store, errors::add);
        List<LiveHeats> seen = new ArrayList<>();
        service.register(seen::add);

        service.accept(TestMessageCreator.used(1, 1, "1100000000"));

        assertThat(errors).singleElement().isInstanceOf(StoreBrokenException.class);
        assertThat(seen).isEmpty();
    }

    private static final class StoreBrokenException extends RuntimeException {
    }

    private static final class InMemoryHeats implements HeatRepository {

        private final Map<List<Integer>, HeatDto> heats = new HashMap<>();
        private boolean failing;

        @Override
        public Optional<HeatDto> find(int event, int heat) {
            return Optional.ofNullable(heats.get(List.of(event, heat)));
        }

        @Override
        public SortedSet<Integer> findUsedLanes() {
            return new TreeSet<>();
        }

        @Override
        public List<HeatDto> findAll() {
            return List.copyOf(heats.values());
        }

        @Override
        public HeatDto create(HeatDto dto) {
            save(dto);
            return dto;
        }

        @Override
        public void save(HeatDto dto) {
            if (failing) {
                throw new StoreBrokenException();
            }
            heats.put(List.of(dto.event(), dto.heat()), dto);
        }
    }
}
