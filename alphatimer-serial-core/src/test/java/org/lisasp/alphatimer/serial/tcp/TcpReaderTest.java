package org.lisasp.alphatimer.serial.tcp;

import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

class TcpReaderTest {

    @Test
    void doesNotConnectBeforeStartAndCloseIsSafe() {
        TcpReader reader = new TcpReader("localhost", 1);
        assertThat(reader.isConnected()).isFalse();
        reader.close();
        reader.close();
    }

    @Test
    void readsBytesAfterStartAndStopsAfterClose() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CopyOnWriteArrayList<Byte> received = new CopyOnWriteArrayList<>();
            TcpReader reader = new TcpReader("localhost", server.getLocalPort());
            reader.register(received::add);
            reader.start();
            try (Socket s = server.accept()) {
                OutputStream out = s.getOutputStream();
                out.write(new byte[]{1, 2, 3});
                out.flush();
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(received).containsExactly((byte) 1, (byte) 2, (byte) 3));
                assertThat(reader.isConnected()).isTrue();
            }
            reader.close();
            reader.close();
            assertThat(reader.isConnected()).isFalse();
        }
    }

    @Test
    void canBeRecreatedOnTheSamePort() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            for (int i = 0; i < 3; i++) {
                TcpReader reader = new TcpReader("localhost", server.getLocalPort());
                reader.start();
                server.accept().close();
                reader.close();
            }
        }
    }

    @Test
    void closeWaitsUntilTheBytesAlreadyReadAreDeliveredAndTheReaderThreadHasEnded() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CopyOnWriteArrayList<Byte> received = new CopyOnWriteArrayList<>();
            CountDownLatch firstByteArrived = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CompletableFuture<Thread> readerThread = new CompletableFuture<>();
            TcpReader reader = new TcpReader("localhost", server.getLocalPort());
            reader.register(b -> {
                received.add(b);
                if (received.size() == 1) {
                    readerThread.complete(Thread.currentThread());
                    firstByteArrived.countDown();
                    awaitIgnoringInterrupts(release);
                }
            });
            reader.start();
            try (Socket s = server.accept()) {
                OutputStream out = s.getOutputStream();
                out.write(new byte[]{1, 2, 3});
                out.flush();
                assertThat(firstByteArrived.await(10, TimeUnit.SECONDS)).isTrue();

                CompletableFuture<List<Byte>> deliveredWhenClosed = CompletableFuture.supplyAsync(() -> {
                    reader.close();
                    return List.copyOf(received);
                });
                CompletableFuture.anyOf(deliveredWhenClosed, CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS))).join();
                release.countDown();

                assertThat(deliveredWhenClosed.get(10, TimeUnit.SECONDS)).containsExactly((byte) 1, (byte) 2, (byte) 3);
                assertThat(readerThread.get().isAlive()).isFalse();
            }
        }
    }

    @Test
    void closeDuringAHangingConnectEndsTheReaderThread() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1)) {
            List<Socket> backlog = fillBacklog(server);
            try {
                TcpReader reader = new TcpReader("localhost", server.getLocalPort());
                reader.start();
                Thread readerThread = await().atMost(Duration.ofSeconds(10)).until(() -> readerThread(server.getLocalPort()), t -> t != null);

                CompletableFuture.runAsync(reader::close).get(10, TimeUnit.SECONDS);

                assertThat(readerThread.isAlive()).isFalse();
                assertThat(reader.isConnected()).isFalse();
            } finally {
                for (Socket s : backlog) {
                    s.close();
                }
            }
        }
    }

    private static List<Socket> fillBacklog(ServerSocket server) throws Exception {
        List<Socket> clients = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Socket client = new Socket();
            clients.add(client);
            try {
                client.connect(new InetSocketAddress("localhost", server.getLocalPort()), 500);
            } catch (SocketTimeoutException full) {
                return clients;
            }
        }
        fail("The backlog of the server never filled up");
        return clients;
    }

    private static Thread readerThread(int port) {
        return Thread.getAllStackTraces().keySet().stream()
                     .filter(t -> t.getName().equals("tcp-reader-localhost:" + port))
                     .findFirst()
                     .orElse(null);
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
