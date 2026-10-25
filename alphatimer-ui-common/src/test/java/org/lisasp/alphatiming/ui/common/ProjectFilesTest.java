package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFilesTest {

    @TempDir
    Path dir;

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

    @Test
    void aChosenNameWithoutExtensionIsSavedWithTheProjectExtension() {
        List<Path> asked = new ArrayList<>();

        assertThat(ProjectFiles.chooseSavePath(chooser(dir.resolve("WK2026")), path -> asked.add(path), path -> { }))
                .contains(dir.resolve("WK2026.timing"));
        assertThat(asked).isEmpty();
    }

    @Test
    void theOverwriteQuestionChecksThePathThatIsWritten() throws IOException {
        Files.createFile(dir.resolve("WK2026.timing"));
        List<Path> asked = new ArrayList<>();

        assertThat(ProjectFiles.chooseSavePath(chooser(dir.resolve("WK2026")), path -> asked.add(path), path -> { }))
                .contains(dir.resolve("WK2026.timing"));
        assertThat(asked).containsExactly(dir.resolve("WK2026.timing"));
    }

    @Test
    void refusingToOverwriteAsksForAnotherName() throws IOException {
        Files.createFile(dir.resolve("WK2026.timing"));

        assertThat(ProjectFiles.chooseSavePath(chooser(dir.resolve("WK2026"), dir.resolve("WK2026-2")), path -> false, path -> { }))
                .contains(dir.resolve("WK2026-2.timing"));
    }

    @Test
    void aFolderIsRejectedWithoutTheOverwriteQuestionAndAnotherNameIsAsked() throws IOException {
        Files.createDirectory(dir.resolve("WK2026.timing"));
        List<Path> asked = new ArrayList<>();
        List<Path> rejected = new ArrayList<>();

        assertThat(ProjectFiles.chooseSavePath(chooser(dir.resolve("WK2026"), dir.resolve("WK2026-2")), path -> asked.add(path), rejected::add))
                .contains(dir.resolve("WK2026-2.timing"));
        assertThat(rejected).containsExactly(dir.resolve("WK2026.timing"));
        assertThat(asked).isEmpty();
    }

    @Test
    void theMessageForARejectedFolderSaysItIsAFolder() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        try {
            assertThat(ProjectFiles.folderMessage(Path.of("wk.timing"))).isEqualTo("Could not save wk.timing: wk.timing is a folder, not a file");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void cancellingTheDialogEndsTheChoice() throws IOException {
        Files.createFile(dir.resolve("WK2026.timing"));
        List<Path> asked = new ArrayList<>();

        assertThat(ProjectFiles.chooseSavePath(chooser(dir.resolve("WK2026"), null), path -> !asked.add(path), path -> { })).isEmpty();
        assertThat(asked).containsExactly(dir.resolve("WK2026.timing"));
    }

    private static Supplier<Optional<Path>> chooser(Path... answers) {
        Iterator<Path> remaining = Arrays.asList(answers).iterator();
        return () -> remaining.hasNext() ? Optional.ofNullable(remaining.next()) : Optional.empty();
    }
}
