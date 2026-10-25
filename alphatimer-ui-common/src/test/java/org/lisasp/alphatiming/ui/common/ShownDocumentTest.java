package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatiming.ui.core.project.TimingDocument;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ShownDocumentTest {

    @TempDir
    Path dir;

    @Test
    void onlyAnotherDocumentCountsAsReplaced() throws Exception {
        try (TimingDocument first = TimingDocument.create(Files.createDirectories(dir.resolve("a")), Settings.defaults());
             TimingDocument second = TimingDocument.create(Files.createDirectories(dir.resolve("b")), Settings.defaults())) {
            ShownDocument shown = new ShownDocument();

            assertThat(shown.replacedBy(first)).isTrue();
            assertThat(shown.replacedBy(first)).isFalse();
            assertThat(shown.replacedBy(second)).isTrue();
            assertThat(shown.replacedBy(second)).isFalse();
        }
    }
}
