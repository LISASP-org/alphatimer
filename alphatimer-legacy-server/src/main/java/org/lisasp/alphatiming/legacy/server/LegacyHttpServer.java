package org.lisasp.alphatiming.legacy.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatiming.legacy.server.dto.Heat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
public final class LegacyHttpServer implements AutoCloseable {

    private static final int THREADS = 4;
    private static final int QUEUE_CAPACITY = 16;
    private static final Duration REQUEST_TIME_LIMIT = Duration.ofSeconds(10);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Supplier<Heat[]> heats;
    private final HttpServer server;
    private final HandlerPool pool;

    public LegacyHttpServer(int port, Supplier<Heat[]> heats) throws IOException {
        this(port, heats, new HandlerPool(THREADS, QUEUE_CAPACITY, REQUEST_TIME_LIMIT));
    }

    LegacyHttpServer(int port, Supplier<Heat[]> heats, HandlerPool pool) throws IOException {
        this.heats = heats;
        this.pool = pool;
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            pool.close();
            throw e;
        }
        this.server.createContext("/", this::handle);
        this.server.setExecutor(pool);
        this.server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            boolean xml = path.equals("/legacy/heats.xml");
            boolean json = path.equals("/legacy/heats.json");
            if (!xml && !json) {
                respond(exchange, 404, "text/plain; charset=UTF-8", "Not found");
            } else if (!exchange.getRequestMethod().equals("GET")) {
                respond(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
            } else {
                serveHeats(exchange, xml);
            }
        }
    }

    private void serveHeats(HttpExchange exchange, boolean xml) throws IOException {
        String body;
        try {
            body = xml ? LegacySerialization.toXML(heats.get()) : mapper.writeValueAsString(heats.get());
        } catch (RuntimeException ex) {
            log.warn("Could not serve request", ex);
            respond(exchange, 500, "text/plain; charset=UTF-8", "Internal error");
            return;
        }
        respond(exchange, 200, xml ? "application/xml; charset=UTF-8" : "application/json", body);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        pool.close();
    }
}
