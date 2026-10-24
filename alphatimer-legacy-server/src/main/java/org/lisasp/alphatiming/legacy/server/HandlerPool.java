package org.lisasp.alphatiming.legacy.server;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
final class HandlerPool implements Executor, AutoCloseable {

    private final ThreadPoolExecutor workers;
    private final ScheduledExecutorService watchdog;
    private final Duration timeout;

    HandlerPool(int threads, int queueCapacity, Duration timeout) {
        this.timeout = timeout;
        workers = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                                         new ArrayBlockingQueue<>(queueCapacity), daemonThreads("legacy-http-"),
                                         new ThreadPoolExecutor.AbortPolicy());
        watchdog = Executors.newSingleThreadScheduledExecutor(daemonThreads("legacy-http-timeout-"));
    }

    private static ThreadFactory daemonThreads(String prefix) {
        AtomicInteger count = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    @Override
    public void execute(Runnable exchange) {
        workers.execute(() -> runWithTimeout(exchange));
    }

    /**
     * The JDK server reads and writes the connection with blocking channel calls on this thread, so the interrupt
     * closes the connection of a client that stalls; this also covers a request that never completes its headers.
     */
    private void runWithTimeout(Runnable exchange) {
        Interrupter interrupter = new Interrupter(Thread.currentThread());
        ScheduledFuture<?> timer = watchdog.schedule(interrupter, timeout.toMillis(), TimeUnit.MILLISECONDS);
        try {
            exchange.run();
        } finally {
            timer.cancel(false);
            interrupter.disarm();
            Thread.interrupted();
        }
    }

    @Override
    public void close() {
        workers.shutdownNow();
        watchdog.shutdownNow();
    }

    private static final class Interrupter implements Runnable {

        private final Thread worker;
        private boolean armed = true;

        Interrupter(Thread worker) {
            this.worker = worker;
        }

        @Override
        public synchronized void run() {
            if (armed) {
                log.warn("REST request took longer than its time limit, closing its connection");
                worker.interrupt();
            }
        }

        synchronized void disarm() {
            armed = false;
        }
    }
}
