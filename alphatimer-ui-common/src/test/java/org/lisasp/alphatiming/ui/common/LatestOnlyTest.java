package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LatestOnlyTest {

    private final List<Runnable> queued = new ArrayList<>();
    private final List<Integer> shown = new ArrayList<>();
    private final LatestOnly<Integer> latest = new LatestOnly<>(queued::add, shown::add);

    @Test
    void manyValuesWhileOneIsPendingAreShownOnceAsTheLast() {
        for (int i = 1; i <= 1000; i++) {
            latest.submit(i);
        }

        assertThat(queued).hasSize(1);
        queued.removeFirst().run();
        assertThat(shown).containsExactly(1000);
    }

    @Test
    void aValueAfterTheLastWasShownIsQueuedAgain() {
        latest.submit(1);
        queued.removeFirst().run();

        latest.submit(2);

        assertThat(queued).hasSize(1);
        queued.removeFirst().run();
        assertThat(shown).containsExactly(1, 2);
    }
}
