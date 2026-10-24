package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFilesTest {

    @Test
    void aNameWithoutExtensionGetsTheProjectExtension() {
        assertThat(ProjectFiles.withDefaultExtension(Path.of("data.x", "competition"))).isEqualTo(Path.of("data.x", "competition.timing"));
    }

    @Test
    void projectFilesOfThisAndEarlierVersionsKeepTheirName() {
        assertThat(ProjectFiles.withDefaultExtension(Path.of("competition.timing"))).isEqualTo(Path.of("competition.timing"));
        assertThat(ProjectFiles.withDefaultExtension(Path.of("competition.heat"))).isEqualTo(Path.of("competition.heat"));
    }

    @Test
    void otherExtensionsArePartOfTheName() {
        assertThat(ProjectFiles.withDefaultExtension(Path.of("wk.2026"))).isEqualTo(Path.of("wk.2026.timing"));
    }
}
