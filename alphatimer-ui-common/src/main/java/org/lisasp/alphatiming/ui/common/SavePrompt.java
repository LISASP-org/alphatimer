package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.ui.core.project.CrashLeftover;

import java.nio.file.Path;
import java.util.Optional;

/** The questions the controller has to ask the user; implemented with dialogs in the UI and with stubs in tests. */
public interface SavePrompt {

    enum Choice { SAVE, DISCARD, CANCEL }

    enum RecoveryChoice { RECOVER, DISCARD, LATER }

    /** Leaving the document would end the running recording; false keeps both. */
    boolean askStopRecording();

    Choice askSaveChanges();

    /** Where to save a project that has no file yet (or Save As); empty = cancelled. */
    Optional<Path> askSavePath();

    void showError(String title, String message);

    RecoveryChoice askRecover(CrashLeftover leftover);

    /** Discarding deletes the leftover for good; false goes back to {@link #askRecover}. */
    boolean confirmDiscard(CrashLeftover leftover);
}
