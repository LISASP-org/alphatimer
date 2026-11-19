package org.lisasp.alphatiming.heats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryHeatRepositoryTest {

    private final InMemoryHeatRepository repository = new InMemoryHeatRepository();

    @Test
    void findAllReturnsTheMostRecentlyChangedHeatFirst() {
        repository.create(HeatDto.empty(1, 1, 10));
        repository.create(HeatDto.empty(1, 2, 10));
        repository.save(HeatDto.empty(1, 1, 10));

        assertThat(repository.findAll()).extracting(HeatDto::heat).containsExactly(1, 2);
    }

    @Test
    void findUsedLanesCollectsTheUsedLanesOfAllHeats() {
        HeatService service = new HeatService(repository);
        service.accept(TestMessageCreator.used(1, 1, "1100000000"));
        service.accept(TestMessageCreator.used(1, 2, "0010000000"));

        assertThat(repository.findUsedLanes()).containsExactly(1, 2, 3);
    }

    @Test
    void savingAnUnknownHeatFails() {
        assertThatThrownBy(() -> repository.save(HeatDto.empty(1, 1, 10))).isInstanceOf(java.util.NoSuchElementException.class);
    }
}
