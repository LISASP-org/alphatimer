package org.lisasp.alphatiming.ui;

import org.lisasp.alphatiming.ui.common.SavePrompt;
import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.common.ProjectFiles;
import lombok.Setter;

import org.lisasp.swing.filechooser.FileChooserUtils;
import org.lisasp.swing.filechooser.filefilter.SimpleFileFilter;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.nio.file.Path;
import java.util.Optional;

/** The dialogs behind {@link SavePrompt}. */
public final class SwingSavePrompt implements SavePrompt {

    @Setter
    private JFrame parent;

    @Override
    public Choice askSaveChanges() {
        Object[] options = {I18n.get("button.save"), I18n.get("button.discard"), I18n.get("button.cancel")};
        int answer = JOptionPane.showOptionDialog(parent,
                                                  I18n.get("dialog.save.message"),
                                                  I18n.get("dialog.save.title"),
                                                  JOptionPane.YES_NO_CANCEL_OPTION,
                                                  JOptionPane.WARNING_MESSAGE,
                                                  null,
                                                  options,
                                                  options[0]);
        return switch (answer) {
            case 0 -> Choice.SAVE;
            case 1 -> Choice.DISCARD;
            default -> Choice.CANCEL;
        };
    }

    @Override
    public boolean askStopRecording() {
        Object[] options = {I18n.get("button.stopRecording"), I18n.get("button.cancel")};
        int answer = JOptionPane.showOptionDialog(parent,
                                                  I18n.get("dialog.stopRecording.message"),
                                                  I18n.get("dialog.stopRecording.title"),
                                                  JOptionPane.OK_CANCEL_OPTION,
                                                  JOptionPane.WARNING_MESSAGE,
                                                  null,
                                                  options,
                                                  options[1]);
        return answer == 0;
    }

    @Override
    public Optional<Path> askSavePath() {
        return ProjectFiles.chooseSavePath(() -> Optional.ofNullable(FileChooserUtils.saveFile(parent, projectFilter())).map(Path::of),
                                           path -> JOptionPane.showConfirmDialog(parent, I18n.get("dialog.overwrite.message", path.getFileName()), I18n.get("dialog.overwrite.title"), JOptionPane.YES_NO_OPTION)
                                                   == JOptionPane.YES_OPTION);
    }

    public Optional<Path> askOpenPath() {
        return Optional.ofNullable(FileChooserUtils.openFile(parent, projectFilter())).map(Path::of);
    }

    static SimpleFileFilter projectFilter() {
        return new SimpleFileFilter(I18n.get("file.filter"), ProjectFiles.EXTENSION, ProjectFiles.EARLIER_EXTENSION);
    }

    @Override
    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public RecoveryChoice askRecover(Path workFile) {
        Object[] options = {I18n.get("button.recover"), I18n.get("button.discard"), I18n.get("button.later")};
        int answer = JOptionPane.showOptionDialog(parent,
                                                  I18n.get("dialog.recover.message"),
                                                  I18n.get("dialog.recover.title"),
                                                  JOptionPane.DEFAULT_OPTION,
                                                  JOptionPane.QUESTION_MESSAGE,
                                                  null,
                                                  options,
                                                  options[0]);
        return switch (answer) {
            case 0 -> RecoveryChoice.RECOVER;
            case 1 -> RecoveryChoice.DISCARD;
            default -> RecoveryChoice.LATER;
        };
    }
}
