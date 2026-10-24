package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.ui.core.project.CrashLeftover;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryTextsTest {

    private static final Path PROJECT = Path.of("/data/wk.timing").toAbsolutePath();
    private static final Instant CHANGED = LocalDateTime.of(2026, 10, 24, 14, 5, 3).atZone(ZoneId.systemDefault()).toInstant();

    private Locale original;

    @BeforeEach
    void rememberLocale() {
        original = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    private static CrashLeftover leftover(Optional<Path> project) {
        return new CrashLeftover(Path.of("work", "heat-1.work.sqlite"), CHANGED, project);
    }

    @Test
    void questionNamesTheProjectFileAndTheLastChangeInTheLocaleFormat() {
        Locale.setDefault(Locale.GERMANY);
        assertThat(RecoveryTexts.question(leftover(Optional.of(PROJECT)))).contains("Projektdatei: " + PROJECT)
                                                                          .contains("Zuletzt geändert: 24.10.2026, 14:05:03")
                                                                          .contains("wiederherstellen?");
    }

    @Test
    void questionSaysWhenThereIsNoProjectFile() {
        Locale.setDefault(Locale.ENGLISH);
        assertThat(RecoveryTexts.question(leftover(Optional.empty()))).contains("Project file: not saved yet")
                                                                      .contains("Last changed: Oct 24, 2026")
                                                                      .contains("recover it?");
    }

    @Test
    void discardQuestionAsksAgainForTheSameLeftover() {
        Locale.setDefault(Locale.GERMANY);
        assertThat(RecoveryTexts.discardQuestion(leftover(Optional.of(PROJECT)))).contains("wirklich")
                                                                                 .contains("Projektdatei: " + PROJECT)
                                                                                 .contains("Zuletzt geändert: 24.10.2026, 14:05:03");
    }
}
