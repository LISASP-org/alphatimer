package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.ui.core.recording.RecordingError;
import org.lisasp.alphatiming.ui.core.recording.RecordingState;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AppStatusTest {

    private Locale original;

    @BeforeEach
    void english() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    void anUnsavedStoppedProjectCanBeStarted() {
        AppStatus status = AppStatus.of(true, Optional.empty(), false, RecordingState.STOPPED, false, null, "");

        assertThat(status.title()).isEqualTo("AlphaTiming – unsaved project");
        assertThat(status.file()).isEqualTo("unsaved project");
        assertThat(status.state()).isEqualTo("Stopped");
        assertThat(status.warning()).isEmpty();
        assertThat(status.canStart()).isTrue();
        assertThat(status.canStop()).isFalse();
        assertThat(status.recording()).isFalse();
    }

    @Test
    void aChangedProjectShowsItsFileNameWithAStar() {
        AppStatus status = AppStatus.of(true, Optional.of(Path.of("data", "wk.timing")), true, RecordingState.STOPPED, false, null, "");

        assertThat(status.title()).isEqualTo("AlphaTiming – wk.timing *");
        assertThat(status.file()).isEqualTo("wk.timing *");
    }

    @Test
    void aRunningRecordingCanOnlyBeStoppedAndTellsWhetherItIsConnected() {
        AppStatus connected = AppStatus.of(true, Optional.empty(), false, RecordingState.RUNNING, true, null, "");
        AppStatus waiting = AppStatus.of(true, Optional.empty(), false, RecordingState.RUNNING, false, null, "");

        assertThat(connected.state()).isEqualTo("Recording – connected");
        assertThat(waiting.state()).isEqualTo("Recording – waiting for connection");
        assertThat(connected.canStart()).isFalse();
        assertThat(connected.canStop()).isTrue();
        assertThat(connected.recording()).isTrue();
    }

    @Test
    void aFailedStartShowsTheErrorAndCanBeStartedAgain() {
        AppStatus status = AppStatus.of(true, Optional.empty(), false, RecordingState.ERROR, false,
                                        new RecordingError(RecordingError.Kind.SERIAL_PORT_NOT_FOUND, "COM7"), "");

        assertThat(status.state()).isEqualTo("Error: Serial port COM7 was not found");
        assertThat(status.canStart()).isTrue();
        assertThat(status.canStop()).isFalse();
    }

    @Test
    void withoutADocumentNothingCanBeStartedAndTheWarningIsPassedOn() {
        AppStatus status = AppStatus.of(false, Optional.empty(), false, RecordingState.STOPPED, false, null, "Auto-save failed");

        assertThat(status.title()).isEqualTo("AlphaTiming – ");
        assertThat(status.file()).isEmpty();
        assertThat(status.canStart()).isFalse();
        assertThat(status.warning()).isEqualTo("Auto-save failed");
    }
}
