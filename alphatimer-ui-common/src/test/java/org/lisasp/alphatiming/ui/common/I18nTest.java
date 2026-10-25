package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.ui.core.recording.RecordingError;
import org.lisasp.alphatiming.ui.core.settings.SerialMode;
import org.lisasp.alphatiming.ui.core.settings.SettingsProblem;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class I18nTest {

    private static final String BASE = "org/lisasp/alphatiming/ui/common/messages";

    /** Every front end uses this bundle, so a key is only dead when none of them uses it. */
    private static final List<Path> SOURCES_USING_THE_BUNDLE = List.of(Path.of("src/main/java"),
                                                                        Path.of("../alphatimer-ui/src/main/java"),
                                                                        Path.of("../alphatimer-cli/src/main/java"),
                                                                        Path.of("../alphatimer-tui/src/main/java"));

    private Locale original;

    @BeforeEach
    void rememberLocale() {
        original = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    private static Properties load(String suffix) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = I18nTest.class.getClassLoader().getResourceAsStream(BASE + suffix + ".properties");
             Reader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Set<String> keys(Properties properties) {
        return new TreeSet<>(properties.stringPropertyNames());
    }

    @Test
    void bothLanguagesHaveExactlyTheSameKeys() throws IOException {
        assertThat(keys(load("_de"))).isEqualTo(keys(load("")));
    }

    @Test
    void noTextIsEmptyAndPlaceholdersMatchBetweenTheLanguages() throws IOException {
        Properties english = load("");
        Properties german = load("_de");
        Pattern placeholder = Pattern.compile("\\{(\\d+)}");
        for (String key : english.stringPropertyNames()) {
            assertThat(english.getProperty(key)).as(key).isNotBlank();
            assertThat(german.getProperty(key)).as(key).isNotBlank();
            assertThat(placeholders(placeholder, german.getProperty(key))).as("placeholders of " + key).isEqualTo(placeholders(placeholder, english.getProperty(key)));
        }
    }

    private static Set<String> placeholders(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        Set<String> found = new TreeSet<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    void everyKeyUsedInTheCodeExistsAndNoKeyIsDead() throws IOException {
        Set<String> defined = keys(load(""));
        Set<String> used = new HashSet<>();
        Pattern literal = Pattern.compile("I18n\\.get\\(\\s*\"([A-Za-z0-9_.]+)\"");
        for (Path sources : SOURCES_USING_THE_BUNDLE) {
            assertThat(sources).isDirectory();
            try (Stream<Path> files = Files.walk(sources)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    Matcher matcher = literal.matcher(Files.readString(file));
                    while (matcher.find()) {
                        if (!matcher.group(1).endsWith(".")) {
                            used.add(matcher.group(1));
                        }
                    }
                }
            }
        }
        for (SettingsProblem problem : SettingsProblem.values()) {
            used.add("problem." + problem.name());
        }
        for (RecordingError.Kind kind : RecordingError.Kind.values()) {
            if (kind != RecordingError.Kind.INVALID_SETTINGS) {
                used.add("error." + kind.name());
            }
        }
        for (org.lisasp.alphatiming.ui.core.settings.ConnectionType type : org.lisasp.alphatiming.ui.core.settings.ConnectionType.values()) {
            used.add("input.type." + type.name());
        }
        for (SerialMode mode : SerialMode.values()) {
            used.add("mode." + mode.name());
        }
        for (org.lisasp.alphatiming.ui.core.recording.RecordedData data : org.lisasp.alphatiming.ui.core.recording.RecordedData.values()) {
            used.add("data." + data.name());
        }
        for (FileFailure.Kind kind : FileFailure.Kind.values()) {
            used.add("failure." + kind.name());
        }
        assertThat(defined).as("keys used in code but not defined").containsAll(used);
        assertThat(used).as("defined but never used").containsAll(defined);
    }

    @Test
    void bothBundlesAreAsciiWithEscapesAndUseNoApostrophes() throws IOException {
        for (String suffix : List.of("", "_de")) {
            byte[] bytes;
            try (InputStream in = I18nTest.class.getClassLoader().getResourceAsStream(BASE + suffix + ".properties")) {
                bytes = in.readAllBytes();
            }
            for (byte b : bytes) {
                assertThat(b).as("non-ASCII byte in messages" + suffix + ".properties").isGreaterThanOrEqualTo((byte) 0);
            }
            Properties properties = load(suffix);
            for (String key : properties.stringPropertyNames()) {
                assertThat(properties.getProperty(key)).as(key + " in messages" + suffix).doesNotContain("'");
            }
        }
    }

    @Test
    void germanIsUsedForGermanLocales() {
        Locale.setDefault(Locale.GERMANY);
        assertThat(I18n.get("menu.file")).isEqualTo("Datei");
        assertThat(I18n.get("status.lastMessage", "10:00:00")).isEqualTo("Letzte Nachricht 10:00:00");
        assertThat(I18n.describe(SettingsProblem.TCP_HOST_EMPTY)).isEqualTo("Der TCP-Host darf nicht leer sein");
    }

    @Test
    void connectionTypesAndSerialModesHaveLabels() {
        Locale.setDefault(Locale.ENGLISH);
        assertThat(I18n.label(org.lisasp.alphatiming.ui.core.settings.ConnectionType.FILE)).isEqualTo("File replay");
        assertThat(I18n.label(SerialMode.QUANTUM)).isEqualTo("Quantum (9600 baud, 8N1)");
    }

    @Test
    void englishIsUsedForEnglishAndForUnsupportedLocales() {
        Locale.setDefault(Locale.US);
        assertThat(I18n.get("menu.file")).isEqualTo("File");
        Locale.setDefault(Locale.FRANCE);
        assertThat(I18n.get("menu.file")).isEqualTo("File");
    }

    @Test
    void unknownKeysShowTheKeyAndDescriptionsJoinSettingsProblems() {
        Locale.setDefault(Locale.ENGLISH);
        assertThat(I18n.get("does.not.exist")).isEqualTo("does.not.exist");
        RecordingError invalid = new RecordingError(RecordingError.Kind.INVALID_SETTINGS, "", List.of(SettingsProblem.TCP_HOST_EMPTY, SettingsProblem.TCP_PORT_RANGE));
        assertThat(I18n.describe(invalid)).isEqualTo("TCP host must not be empty; TCP port must be between 1 and 65535");
        assertThat(I18n.describe(new RecordingError(RecordingError.Kind.SERIAL_PORT_NOT_FOUND, "COM7"))).isEqualTo("Serial port COM7 was not found");
        assertThat(I18n.describe(new RecordingError(RecordingError.Kind.REST_PORT, "1999"))).contains("1999");
    }
}
