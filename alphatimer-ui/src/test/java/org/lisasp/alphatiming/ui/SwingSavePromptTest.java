package org.lisasp.alphatiming.ui;

import org.junit.jupiter.api.Test;
import org.lisasp.swing.filechooser.filefilter.SimpleFileFilter;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SwingSavePromptTest {

    @Test
    void projectsAreSavedAsTimingFilesAndOlderHeatFilesStillOpen() {
        SimpleFileFilter filter = SwingSavePrompt.projectFilter();
        assertThat(filter.getSuffix()).isEqualTo(".timing");
        assertThat(filter.accept(new File("competition.timing"))).isTrue();
        assertThat(filter.accept(new File("competition.heat"))).isTrue();
    }

    @Test
    void aQuestionAskedFromABackgroundThreadIsAnsweredOnTheEdt() throws Exception {
        AtomicBoolean askedOnEdt = new AtomicBoolean();

        String answer = CompletableFuture.supplyAsync(() -> SwingSavePrompt.answerOnEdt(() -> {
            askedOnEdt.set(SwingUtilities.isEventDispatchThread());
            return "answer";
        }, "interrupted")).get(10, TimeUnit.SECONDS);

        assertThat(askedOnEdt).isTrue();
        assertThat(answer).isEqualTo("answer");
    }

    @Test
    void aQuestionAskedOnTheEdtIsAnsweredDirectly() throws Exception {
        String[] answer = new String[1];

        SwingUtilities.invokeAndWait(() -> answer[0] = SwingSavePrompt.answerOnEdt(() -> "answer", "interrupted"));

        assertThat(answer[0]).isEqualTo("answer");
    }
}
