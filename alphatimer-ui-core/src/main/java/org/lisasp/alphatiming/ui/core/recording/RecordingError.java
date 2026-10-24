package org.lisasp.alphatiming.ui.core.recording;

import org.lisasp.alphatiming.ui.core.settings.SettingsProblem;

import java.util.List;

/** Why a recording could not start; the UI turns it into text. */
public record RecordingError(Kind kind, String detail, List<SettingsProblem> problems) {

    public enum Kind {
        INVALID_SETTINGS,
        REST_PORT,
        SERIAL_PORT_NOT_FOUND,
        SERIAL_PORT_IN_USE,
        SERIAL_PORT_NAME_TOO_LONG,
        SERIAL_UNAVAILABLE,
        SERIAL_OPEN_FAILED,
        REPLAY_FILE_NOT_FOUND,
        REPLAY_FILE_UNREADABLE,
        UNEXPECTED
    }

    public RecordingError(Kind kind, String detail) {
        this(kind, detail, List.of());
    }
}
