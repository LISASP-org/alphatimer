package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SignOfLifeTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 19, 10, 0);

    private final AtomicReference<LocalDateTime> now = new AtomicReference<>(T0);
    private final SignOfLife signOfLife = new SignOfLife(now::get);

    @Test
    void withoutAnythingReceivedThereIsNoRecentSignOfLife() {
        assertThat(signOfLife.isRecent()).isFalse();
    }

    @Test
    void whatWasReceivedIsRecentForFiveSeconds() {
        signOfLife.received();
        assertThat(signOfLife.isRecent()).isTrue();

        now.set(T0.plusSeconds(5));
        assertThat(signOfLife.isRecent()).isTrue();
    }

    @Test
    void afterFiveSecondsWithoutAnythingReceivedItIsNoLongerRecent() {
        signOfLife.received();

        now.set(T0.plusSeconds(5).plusNanos(1_000_000));
        assertThat(signOfLife.isRecent()).isFalse();
    }

    @Test
    void aForgottenSignOfLifeIsNotRecent() {
        signOfLife.received();

        signOfLife.forget();

        assertThat(signOfLife.isRecent()).isFalse();
    }
}
