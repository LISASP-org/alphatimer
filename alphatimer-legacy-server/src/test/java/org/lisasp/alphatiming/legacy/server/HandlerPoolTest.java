package org.lisasp.alphatiming.legacy.server;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class HandlerPoolTest {

    private static final Duration NO_TIMEOUT_IN_TEST = Duration.ofMinutes(5);

    @Test
    void rejectsATaskWhenAllThreadsAreBusyAndTheQueueIsFull() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch queuedRan = new CountDownLatch(1);
        try (HandlerPool pool = new HandlerPool(1, 1, NO_TIMEOUT_IN_TEST)) {
            pool.execute(() -> {
                running.countDown();
                awaitQuietly(release);
            });
            assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();
            pool.execute(queuedRan::countDown);

            assertThatThrownBy(() -> pool.execute(() -> { })).isInstanceOf(RejectedExecutionException.class);

            release.countDown();
            assertThat(queuedRan.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void runsOnNamedDaemonThreadsThatEndOnClose() throws Exception {
        List<Thread> workers = new CopyOnWriteArrayList<>();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch running = new CountDownLatch(2);
        HandlerPool pool = new HandlerPool(2, 1, NO_TIMEOUT_IN_TEST);
        for (int i = 0; i < 2; i++) {
            pool.execute(() -> {
                workers.add(Thread.currentThread());
                running.countDown();
                awaitQuietly(release);
            });
        }
        assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(workers).allSatisfy(t -> {
            assertThat(t.getName()).matches("legacy-http-\\d+");
            assertThat(t.isDaemon()).isTrue();
        });

        pool.close();
        await().atMost(Duration.ofSeconds(10)).until(() -> workers.stream().noneMatch(Thread::isAlive));
        await().atMost(Duration.ofSeconds(10))
               .until(() -> Thread.getAllStackTraces().keySet().stream()
                                  .noneMatch(t -> t.getName().startsWith("legacy-http")));
    }

    @Test
    void interruptsATaskThatRunsLongerThanTheTimeoutAndRunsTheNextOneUndisturbed() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch nextRan = new CountDownLatch(1);
        AtomicBoolean nextStartedInterrupted = new AtomicBoolean(true);
        try (HandlerPool pool = new HandlerPool(1, 1, Duration.ofMillis(200))) {
            pool.execute(() -> {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
            });
            assertThat(interrupted.await(10, TimeUnit.SECONDS)).isTrue();

            pool.execute(() -> {
                nextStartedInterrupted.set(Thread.currentThread().isInterrupted());
                nextRan.countDown();
            });
            assertThat(nextRan.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(nextStartedInterrupted).isFalse();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
