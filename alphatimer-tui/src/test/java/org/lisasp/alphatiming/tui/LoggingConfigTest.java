package org.lisasp.alphatiming.tui;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.ConsoleAppender;
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
    void theShippedConfigurationLogsIntoAFileOnlySoNothingDrawsOverTheScreen() throws Exception {
        URL config = getClass().getResource("/logback.xml");
        assertThat(config).isNotNull();

        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(config);
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.iteratorForAppenders().forEachRemaining(appender -> assertThat(appender).isNotInstanceOf(ConsoleAppender.class));
        context.getLogger("org.lisasp.alphatiming.test").info("hello from the test");
        context.stop();

        try (Stream<Path> files = Files.list(home.resolve(".alphatiming").resolve("logs"))) {
            Path logFile = files.filter(f -> f.getFileName().toString().matches("alphatiming-tui-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}\\.log")).findFirst().orElseThrow();
            assertThat(Files.readString(logFile)).contains("hello from the test");
        }
    }
}
