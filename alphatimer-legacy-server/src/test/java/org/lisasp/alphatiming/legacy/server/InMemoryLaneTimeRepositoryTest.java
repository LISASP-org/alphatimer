package org.lisasp.alphatiming.legacy.server;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLaneTimeRepositoryTest {

    private static LaneTime time(int lane, long millis) {
        return new LaneTime("WK", 1, 1, "", lane, millis, null, LocalDateTime.of(2021, 6, 21, 14, 53));
    }

    @Test
    void deleteRemovesEveryMatchingEntryAndKeepsTheOrderOfTheOthers() {
        InMemoryLaneTimeRepository repository = new InMemoryLaneTimeRepository();
        repository.save(time(1, 1000));
        repository.save(time(2, 2000));
        repository.save(time(1, 1000));
        repository.save(time(1, 3000));

        repository.delete(1, 1, "", 1, 1000);

        assertThat(repository.findAll()).extracting(LaneTime::timeInMillis).containsExactly(2000L, 3000L);
    }

    @Test
    void deleteKeepsTheSameTimeOfAnotherRound() {
        InMemoryLaneTimeRepository repository = new InMemoryLaneTimeRepository();
        repository.save(time(1, 1000));
        repository.save(new LaneTime("WK", 1, 1, "Final", 1, 1000, null, LocalDateTime.of(2021, 6, 21, 14, 53)));

        repository.delete(1, 1, "", 1, 1000);

        assertThat(repository.findAll()).extracting(LaneTime::round).containsExactly("Final");
    }
}
