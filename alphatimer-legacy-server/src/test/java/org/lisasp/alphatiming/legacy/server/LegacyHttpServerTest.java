package org.lisasp.alphatiming.legacy.server;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.legacy.server.dto.Heat;
import org.lisasp.alphatiming.legacy.server.dto.LaneStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
