package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusWarningsTest {

    private final StatusWarnings warnings = new StatusWarnings();
    private final StatusWarnings.Warning autoSave = new StatusWarnings.Warning("Auto-save failed", "disk full");
    private final StatusWarnings.Warning write = new StatusWarnings.Warning("Data not stored", "Heats: disk full");

    @Test
    void withoutProblemsNothingIsActive() {
        assertThat(warnings.active()).isEmpty();
    }

    @Test
    void activeWarningsAreListedMostImportantFirstWhateverTheOrderTheyWereRaisedIn() {
        warnings.raise(StatusWarnings.Kind.AUTO_SAVE, autoSave);
        warnings.raise(StatusWarnings.Kind.RECORDING_WRITE, write);
        assertThat(warnings.active()).containsExactly(write, autoSave);
    }

    @Test
    void raisingAKindAgainReplacesItsWarningAndClearingRemovesOnlyThatKind() {
        StatusWarnings.Warning later = new StatusWarnings.Warning("Auto-save failed", "no permission");
        warnings.raise(StatusWarnings.Kind.AUTO_SAVE, autoSave);
        warnings.raise(StatusWarnings.Kind.RECORDING_WRITE, write);
        warnings.raise(StatusWarnings.Kind.AUTO_SAVE, later);
        warnings.clear(StatusWarnings.Kind.RECORDING_WRITE);
        assertThat(warnings.active()).containsExactly(later);
    }
}
