package org.lisasp.alphatiming.ui.common;

import java.nio.file.Path;
import java.util.Optional;

/** The questions the controller has to ask the user; implemented with dialogs in the UI and with stubs in tests. */
public interface SavePrompt {

    enum Choice { SAVE, DISCARD, CANCEL }

    enum RecoveryChoice { RECOVER, DISCARD, LATER }

    /** The current project has unsaved changes. */
    Choice askSaveChanges();

    /** Where to save a project that has no file yet (or Save As); empty = cancelled. */
    Optional<Path> askSavePath();

    void showError(String title, String message);

    /** A work file of an earlier run was left behind (crash). */
    RecoveryChoice askRecover(Path workFile);
}
