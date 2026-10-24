package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.ui.core.project.ProjectDocument;
import org.lisasp.alphatiming.ui.core.recording.RecordingError;
import org.lisasp.alphatiming.ui.core.recording.RecordingSession;
import org.lisasp.alphatiming.ui.core.recording.RecordingState;

import java.nio.file.Path;
import java.util.Optional;

/** What every front end shows about the open project and the recording. */
public record AppStatus(String title, String file, String state, String warning, boolean canStart, boolean canStop, boolean recording) {

    public static AppStatus of(AppController controller) {
        ProjectDocument doc = controller.document();
        RecordingSession session = controller.session();
        RecordingState state = session == null ? RecordingState.STOPPED : session.state();
        return of(doc != null,
                  doc == null ? Optional.empty() : doc.file(),
                  doc != null && doc.isDirty(),
                  state,
                  session != null && session.isConnected(),
                  session == null ? null : session.error(),
                  controller.warning());
    }

    public static AppStatus of(boolean hasDocument, Optional<Path> file, boolean dirty, RecordingState state, boolean connected, RecordingError error, String warning) {
        String name = hasDocument ? file.map(p -> p.getFileName().toString()).orElse(I18n.get("status.unsavedProject")) : "";
        String shownName = name + (dirty ? " *" : "");
        String stateText = switch (state) {
            case RUNNING -> connected ? I18n.get("status.recordingConnected") : I18n.get("status.recordingWaiting");
            case ERROR -> I18n.get("status.error", I18n.describe(error));
            case STOPPED -> I18n.get("status.stopped");
        };
        boolean running = state == RecordingState.RUNNING;
        return new AppStatus(I18n.get("app.title") + " – " + shownName, shownName, stateText, warning, !running && hasDocument, running, running);
    }
}
