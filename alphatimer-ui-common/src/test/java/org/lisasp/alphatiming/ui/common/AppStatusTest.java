package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.ui.core.recording.RecordingError;
import org.lisasp.alphatiming.ui.core.recording.RecordingState;

import java.nio.file.Path;
import java.util.List;
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
        AppStatus status = AppStatus.of(true, Optional.empty(), false, RecordingState.STOPPED, false, null, List.of());

        assertThat(status.title()).isEqualTo("AlphaTiming – unsaved project");
        assertThat(status.file()).isEqualTo("unsaved project");
        assertThat(status.state()).isEqualTo("Stopped");
        assertThat(status.warning()).isEmpty();
        assertThat(status.warningDetails()).isEmpty();
        assertThat(status.canStart()).isTrue();
        assertThat(status.canStop()).isFalse();
        assertThat(status.recording()).isFalse();
    }

    @Test
    void aChangedProjectShowsItsFileNameWithAStar() {
        AppStatus status = AppStatus.of(true, Optional.of(Path.of("data", "wk.timing")), true, RecordingState.STOPPED, false, null, List.of());

        assertThat(status.title()).isEqualTo("AlphaTiming – wk.timing *");
        assertThat(status.file()).isEqualTo("wk.timing *");
    }

    @Test
    void aRunningRecordingCanOnlyBeStoppedAndTellsWhetherItIsConnected() {
        AppStatus connected = AppStatus.of(true, Optional.empty(), false, RecordingState.RUNNING, true, null, List.of());
        AppStatus waiting = AppStatus.of(true, Optional.empty(), false, RecordingState.RUNNING, false, null, List.of());

        assertThat(connected.state()).isEqualTo("Recording – connected");
        assertThat(waiting.state()).isEqualTo("Recording – waiting for connection");
        assertThat(connected.canStart()).isFalse();
        assertThat(connected.canStop()).isTrue();
        assertThat(connected.recording()).isTrue();
    }

    @Test
    void aFailedStartShowsTheErrorAndCanBeStartedAgain() {
        AppStatus status = AppStatus.of(true, Optional.empty(), false, RecordingState.ERROR, false,
                                        new RecordingError(RecordingError.Kind.SERIAL_PORT_NOT_FOUND, "COM7"), List.of());

        assertThat(status.state()).isEqualTo("Error: Serial port COM7 was not found");
        assertThat(status.canStart()).isTrue();
        assertThat(status.canStop()).isFalse();
    }

    @Test
    void withoutADocumentNothingCanBeStartedAndTheWarningIsPassedOn() {
        AppStatus status = AppStatus.of(false, Optional.empty(), false, RecordingState.STOPPED, false, null,
                                        List.of(new StatusWarnings.Warning("Auto-save failed", "Auto-save failed: disk full")));

        assertThat(status.title()).isEqualTo("AlphaTiming – ");
        assertThat(status.file()).isEmpty();
        assertThat(status.canStart()).isFalse();
        assertThat(status.warning()).isEqualTo("Auto-save failed");
        assertThat(status.warningDetails()).isEqualTo("Auto-save failed: disk full");
    }

    @Test
    void severalWarningsShowTheMostImportantOneWithTheCountOfTheOthersAndAllDetails() {
        AppStatus status = AppStatus.of(true, Optional.empty(), false, RecordingState.RUNNING, true, null,
                                        List.of(new StatusWarnings.Warning("Data not stored", "Heats could not be stored: disk full"),
                                                new StatusWarnings.Warning("Auto-save failed", "Auto-save failed: disk full")));

        assertThat(status.warning()).isEqualTo("Data not stored (+1)");
        assertThat(status.warningDetails()).isEqualTo("Heats could not be stored: disk full\nAuto-save failed: disk full");
    }
}
