package org.lisasp.alphatiming.ui;

import org.junit.jupiter.api.Test;
import org.lisasp.swing.filechooser.filefilter.SimpleFileFilter;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class SwingSavePromptTest {

    @Test
    void projectsAreSavedAsTimingFilesAndOlderHeatFilesStillOpen() {
        SimpleFileFilter filter = SwingSavePrompt.projectFilter();
        assertThat(filter.getSuffix()).isEqualTo(".timing");
        assertThat(filter.accept(new File("competition.timing"))).isTrue();
        assertThat(filter.accept(new File("competition.heat"))).isTrue();
    }
}
