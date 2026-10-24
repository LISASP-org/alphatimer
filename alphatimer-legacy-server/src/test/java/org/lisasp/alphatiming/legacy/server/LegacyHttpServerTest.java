package org.lisasp.alphatiming.legacy.server;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.legacy.server.dto.Heat;
import org.lisasp.alphatiming.legacy.server.dto.LaneStatus;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyHttpServerTest {

    private static Heat[] heats() {
        Heat heat = new Heat("1", 3, 4);
        heat.store(1, 12_345, LaneStatus.RaceTimes);
        return new Heat[]{heat};
    }

    private static HttpResponse<String> send(HttpClient client, int port, String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                                         .method(method, HttpRequest.BodyPublishers.noBody())
                                         .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesXmlAndJsonAndRejectsOthers() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        try (LegacyHttpServer server = new LegacyHttpServer(0, LegacyHttpServerTest::heats)) {
            int port = server.getPort();
            HttpResponse<String> xml = send(client, port, "GET", "/legacy/heats.xml");
            assertThat(xml.statusCode()).isEqualTo(200);
            assertThat(xml.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("application/xml"));
            assertThat(xml.body()).isEqualTo(LegacySerialization.toXML(heats()));

            HttpResponse<String> json = send(client, port, "GET", "/legacy/heats.json");
            assertThat(json.statusCode()).isEqualTo(200);
            assertThat(json.body()).contains("\"event\":3").contains("\"heat\":4");

            assertThat(send(client, port, "GET", "/nope").statusCode()).isEqualTo(404);
            assertThat(send(client, port, "POST", "/legacy/heats.xml").statusCode()).isEqualTo(405);
        }
    }

    @Test
    void aFailureWhileCollectingTheHeatsAnswersWithStatus500() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        try (LegacyHttpServer server = new LegacyHttpServer(0, () -> {
            throw new IllegalStateException("database closed");
        })) {
            assertThat(send(client, server.getPort(), "GET", "/legacy/heats.xml").statusCode()).isEqualTo(500);
            assertThat(send(client, server.getPort(), "GET", "/legacy/heats.json").statusCode()).isEqualTo(500);
        }
    }

    @Test
    void aStalledClientIsDisconnectedAfterTheTimeLimitSoOthersAreServed() throws Exception {
        try (LegacyHttpServer server = new LegacyHttpServer(0, LegacyHttpServerTest::heats,
                                                            new HandlerPool(1, 1, Duration.ofMillis(300)));
             Socket stalled = new Socket("localhost", server.getPort())) {
            stalled.getOutputStream().write("GET /legacy/heats.xml HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
            CompletableFuture<String> stalledAnswer = readAll(stalled);

            assertThat(stalledAnswer.get(10, TimeUnit.SECONDS)).isEmpty();
            String answer = readAll(request(server.getPort())).get(10, TimeUnit.SECONDS);
            assertThat(answer).startsWith("HTTP/1.1 200").endsWith(LegacySerialization.toXML(heats()));
        }
    }

    @Test
    void aRequestBeyondTheBusyThreadsAndTheFullQueueIsClosedWithoutAnswer() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Supplier<Heat[]> blockingHeats = () -> {
            running.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return heats();
        };
        try (LegacyHttpServer server = new LegacyHttpServer(0, blockingHeats,
                                                            new HandlerPool(1, 1, Duration.ofMinutes(5)))) {
            CompletableFuture<String> first = readAll(request(server.getPort()));
            assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<String> second = readAll(request(server.getPort()));
            CompletableFuture<String> third = readAll(request(server.getPort()));

            String rejected = (String) CompletableFuture.anyOf(second, third).get(10, TimeUnit.SECONDS);
            assertThat(rejected).isEmpty();

            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).startsWith("HTTP/1.1 200");
            assertThat(List.of(second.get(10, TimeUnit.SECONDS), third.get(10, TimeUnit.SECONDS)))
                    .filteredOn(answer -> answer.startsWith("HTTP/1.1 200"))
                    .hasSize(1);
        }
    }

    private static Socket request(int port) throws IOException {
        Socket socket = new Socket("localhost", port);
        String request = "GET /legacy/heats.xml HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
        socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
        return socket;
    }

    /**
     * A connection the server closes without reading the request may end in a reset instead of an end of stream;
     * both count as "no answer".
     */
    private static CompletableFuture<String> readAll(Socket socket) {
        return CompletableFuture.supplyAsync(() -> {
            try (socket) {
                return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }

    @Test
    void portIsReleasedOnCloseSoItCanBeReused() throws Exception {
        int port;
        try (LegacyHttpServer first = new LegacyHttpServer(0, LegacyHttpServerTest::heats)) {
            port = first.getPort();
        }
        try (LegacyHttpServer second = new LegacyHttpServer(port, LegacyHttpServerTest::heats)) {
            assertThat(second.getPort()).isEqualTo(port);
        }
    }
}
