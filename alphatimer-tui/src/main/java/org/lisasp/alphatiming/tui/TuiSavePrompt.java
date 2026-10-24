package org.lisasp.alphatiming.tui;

import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.ActionListDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.FileDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.common.ProjectFiles;
import org.lisasp.alphatiming.ui.common.RecoveryTexts;
import org.lisasp.alphatiming.ui.common.SavePrompt;
import org.lisasp.alphatiming.ui.core.project.CrashLeftover;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** The dialogs behind {@link SavePrompt}, as text dialogs. */
final class TuiSavePrompt implements SavePrompt {

    private final WindowBasedTextGUI gui;

    TuiSavePrompt(WindowBasedTextGUI gui) {
        this.gui = gui;
    }

    @Override
    public Choice askSaveChanges() {
        return choose(I18n.get("dialog.save.title"), I18n.get("dialog.save.message"), Choice.CANCEL,
                      I18n.get("button.save"), Choice.SAVE,
                      I18n.get("button.discard"), Choice.DISCARD,
                      I18n.get("button.cancel"), Choice.CANCEL);
    }

    @Override
    public boolean askStopRecording() {
        return choose(I18n.get("dialog.stopRecording.title"), I18n.get("dialog.stopRecording.message"), false,
                      I18n.get("button.cancel"), false,
                      I18n.get("button.stopRecording"), true);
    }

    @Override
    public Optional<Path> askSavePath() {
        return ProjectFiles.chooseSavePath(() -> Optional.ofNullable(new FileDialogBuilder().setTitle(I18n.get("menu.saveAs")).setActionLabel(I18n.get("button.save")).build().showDialog(gui)).map(File::toPath),
                                           path -> choose(I18n.get("dialog.overwrite.title"), I18n.get("dialog.overwrite.message", path.getFileName()), false,
                                                          I18n.get("button.overwrite"), true,
                                                          I18n.get("button.cancel"), false));
    }

    Optional<Path> askOpenPath() {
        File chosen = new FileDialogBuilder().setTitle(I18n.get("menu.open")).setActionLabel(I18n.get("menu.open")).build().showDialog(gui);
        return Optional.ofNullable(chosen).map(File::toPath);
    }

    @Override
    public void showError(String title, String message) {
        new MessageDialogBuilder().setTitle(title).setText(message).addButton(MessageDialogButton.OK).build().showDialog(gui);
    }

    @Override
    public RecoveryChoice askRecover(CrashLeftover leftover) {
        return choose(I18n.get("dialog.recover.title"), RecoveryTexts.question(leftover), RecoveryChoice.LATER,
                      I18n.get("button.recover"), RecoveryChoice.RECOVER,
                      I18n.get("button.discard"), RecoveryChoice.DISCARD,
                      I18n.get("button.later"), RecoveryChoice.LATER);
    }

    @Override
    public boolean confirmDiscard(CrashLeftover leftover) {
        return choose(I18n.get("dialog.discard.title"), RecoveryTexts.discardQuestion(leftover), false,
                      I18n.get("button.back"), false,
                      I18n.get("button.delete"), true);
    }

    /** Alternating labels and answers; Escape gives {@code onCancel}. */
    @SuppressWarnings("unchecked")
    private <T> T choose(String title, String message, T onCancel, Object... labelsAndAnswers) {
        AtomicReference<T> answer = new AtomicReference<>(onCancel);
        ActionListDialogBuilder builder = new ActionListDialogBuilder().setTitle(title).setDescription(message).setCanCancel(true);
        for (int i = 0; i < labelsAndAnswers.length; i += 2) {
            T value = (T) labelsAndAnswers[i + 1];
            builder.addAction((String) labelsAndAnswers[i], () -> answer.set(value));
        }
        builder.build().showDialog(gui);
        return answer.get();
    }
}
