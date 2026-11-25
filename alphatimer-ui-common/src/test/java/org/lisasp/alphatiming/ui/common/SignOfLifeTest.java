package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SignOfLifeTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 19, 10, 0);

    private final AtomicReference<LocalDateTime> now = new AtomicReference<>(T0);
    private final SignOfLife signOfLife = new SignOfLife(now::get);

    @Test
    void withoutAnythingReceivedThereIsNoRecentSignOfLife() {
        assertThat(signOfLife.isRecent(SignOfLife.STREAM_SILENT_AFTER)).isFalse();
    }

    @Test
    void whatWasReceivedIsRecentForFiveSeconds() {
        signOfLife.received();
        assertThat(signOfLife.isRecent(SignOfLife.STREAM_SILENT_AFTER)).isTrue();

        now.set(T0.plusSeconds(5));
        assertThat(signOfLife.isRecent(SignOfLife.STREAM_SILENT_AFTER)).isTrue();
    }

    @Test
    void afterFiveSecondsWithoutAnythingReceivedItIsNoLongerRecent() {
        signOfLife.received();

        now.set(T0.plusSeconds(5).plusNanos(1_000_000));
        assertThat(signOfLife.isRecent(SignOfLife.STREAM_SILENT_AFTER)).isFalse();
    }

    @Test
    void howLongASignOfLifeIsRecentDependsOnTheInput() {
        signOfLife.received();

        now.set(T0.plusSeconds(15));
        assertThat(signOfLife.isRecent(Duration.ofSeconds(15))).isTrue();

        now.set(T0.plusSeconds(15).plusNanos(1_000_000));
        assertThat(signOfLife.isRecent(Duration.ofSeconds(15))).isFalse();
    }

    @Test
    void aForgottenSignOfLifeIsNotRecent() {
        signOfLife.received();

        signOfLife.forget();

        assertThat(signOfLife.isRecent(SignOfLife.STREAM_SILENT_AFTER)).isFalse();
    }

    @Test
    void aPingIsNoMessage() {
        signOfLife.received();

        assertThat(signOfLife.isShowingMessage()).isFalse();
    }

    @Test
    void aMessageIsShownForOneHundredMillisecondsAndStaysASignOfLife() {
        signOfLife.messageReceived();
        assertThat(signOfLife.isShowingMessage()).isTrue();
        assertThat(signOfLife.isRecent(SignOfLife.STREAM_SILENT_AFTER)).isTrue();

        now.set(T0.plusNanos(100_000_000));
        assertThat(signOfLife.isShowingMessage()).isTrue();

        now.set(T0.plusNanos(101_000_000));
        assertThat(signOfLife.isShowingMessage()).isFalse();
        assertThat(signOfLife.isRecent(SignOfLife.STREAM_SILENT_AFTER)).isTrue();
    }

    @Test
    void aForgottenMessageIsNotShown() {
        signOfLife.messageReceived();

        signOfLife.forget();

        assertThat(signOfLife.isShowingMessage()).isFalse();
    }
}
