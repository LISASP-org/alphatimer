package org.lisasp.alphatiming.ui;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingConfigTest {

    @TempDir
    Path home;

    private String originalHome;

    @BeforeEach
    void useTemporaryHome() {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
    }

    @AfterEach
    void restoreHome() {
        System.setProperty("user.home", originalHome);
    }

    @Test
    void theShippedConfigurationWritesALogFileIntoTheAlphaTimingHome() throws Exception {
        URL config = getClass().getResource("/logback.xml");
        assertThat(config).isNotNull();

        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(config);
        context.getLogger("org.lisasp.alphatiming.test").info("hello from the test");
        context.getLogger("org.hibernate.test").debug("hibernate debug is filtered");
        context.stop();

        Path logs = home.resolve(".alphatiming").resolve("logs");
        try (Stream<Path> files = Files.list(logs)) {
            Path logFile = files.filter(f -> f.getFileName().toString().matches("alphatiming-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}\\.log")).findFirst().orElseThrow();
            String content = Files.readString(logFile);
            assertThat(content).contains("hello from the test");
            assertThat(content).doesNotContain("hibernate debug");
        }
    }
}
