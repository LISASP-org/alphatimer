package org.lisasp.alphatiming.ui.core.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class AutoSaverTest {

    private static final Settings SETTINGS = new Settings("host", 8585, 60, 0).withCompetitionKey("WK");

    @TempDir
    Path dir;

    @Test
    void savesDirtyDocumentWithFile() throws Exception {
        try (ProjectDocument doc = ProjectDocument.create(Files.createDirectories(dir.resolve("w")), SETTINGS);
             AutoSaver saver = new AutoSaver(() -> doc, e -> { })) {
            doc.saveAs(dir.resolve("x.heat"));
            doc.updateSettings(SETTINGS.withTcp("b", 2));
            saver.runOnce();
            assertThat(doc.isDirty()).isFalse();
        }
        try (ProjectDocument reopened = ProjectDocument.open(dir.resolve("x.heat"), Files.createDirectories(dir.resolve("w2")))) {
            assertThat(reopened.settings().tcpHost()).isEqualTo("b");
        }
    }

    @Test
    void doesNothingForCleanOrFilelessDocuments() throws Exception {
        List<Exception> errors = new ArrayList<>();
        try (ProjectDocument doc = ProjectDocument.create(Files.createDirectories(dir.resolve("w")), SETTINGS);
             AutoSaver saver = new AutoSaver(() -> doc, errors::add)) {
            doc.updateSettings(SETTINGS.withTcp("changed", 1));
            saver.runOnce(); // dirty but no file
            assertThat(doc.isDirty()).isTrue();
            doc.saveAs(dir.resolve("x.heat"));
            saver.runOnce(); // clean
            assertThat(errors).isEmpty();
        }
        try (AutoSaver saver = new AutoSaver(() -> null, errors::add)) {
            saver.runOnce();
            assertThat(errors).isEmpty();
        }
    }

    @Test
    void anInvalidIntervalIsRejectedAndTheCurrentScheduleKeepsRunning() throws Exception {
        try (ProjectDocument doc = ProjectDocument.create(Files.createDirectories(dir.resolve("w")), SETTINGS);
             AutoSaver saver = new AutoSaver(() -> doc, e -> { })) {
            saver.setIntervalSeconds(1);
            assertThatThrownBy(() -> saver.setIntervalSeconds(0)).isInstanceOf(IllegalArgumentException.class);
            doc.saveAs(dir.resolve("x.heat"));
            doc.updateSettings(SETTINGS.withTcp("b", 2));
            await().atMost(Duration.ofSeconds(5)).until(() -> !doc.isDirty());
        }
    }

    @Test
    void reportsFailuresAndKeepsDocumentDirty() throws Exception {
        List<Exception> errors = new ArrayList<>();
        try (ProjectDocument doc = ProjectDocument.create(Files.createDirectories(dir.resolve("w")), SETTINGS);
             AutoSaver saver = new AutoSaver(() -> doc, errors::add)) {
            Path target = dir.resolve("sub/x.heat");
            Files.createDirectories(target.getParent());
            doc.saveAs(target);
            doc.updateSettings(SETTINGS.withTcp("b", 2));
            Files.delete(target);
            Files.delete(target.resolveSibling("x.heat.lock"));
            Files.delete(target.getParent());
            saver.runOnce();
            assertThat(errors).hasSize(1);
            assertThat(doc.isDirty()).isTrue();
        }
    }
}
