package org.lisasp.alphatiming.ui.core.settings;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public final class DefaultSettingsStore {

    /** A file of a later version may hold choices this version does not know; they fall back to the built-in default of that field. */
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                                          .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    private final Path file;

    public DefaultSettingsStore(Path file) {
        this.file = file;
    }

    public static DefaultSettingsStore inUserHome() {
        return new DefaultSettingsStore(Path.of(System.getProperty("user.home"), ".alphatiming", "defaults.json"));
    }

    public Settings load() {
        try {
            if (!Files.exists(file)) {
                return Settings.defaults();
            }
            JsonNode tree = mapper.readTree(file.toFile());
            if (tree instanceof ObjectNode object && !object.has("restEnabled")) {
                int port = object.path("restPort").asInt(Settings.DEFAULT_REST_PORT);
                object.put("restEnabled", port != 0);
                object.put("restPort", port == 0 ? Settings.DEFAULT_REST_PORT : port);
            }
            Settings s = mapper.treeToValue(tree, Settings.class);
            return s == null ? Settings.defaults() : s;
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not read default settings {}; using built-in defaults.", file, ex);
            return Settings.defaults();
        }
    }

    public void save(Settings settings) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), settings);
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not write default settings {}", file, ex);
        }
    }
}
