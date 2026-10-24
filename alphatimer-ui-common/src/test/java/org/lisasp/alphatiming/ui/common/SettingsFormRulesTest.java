package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsFormRulesTest {

    private static final Settings TCP = new Settings("localhost", 8585, 60, 1999).withCompetitionKey("WK").withRest(1999, false);

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
    void validTcpSettingsCanBeEditedAndRecorded() {
        SettingsFormRules rules = SettingsFormRules.of(TCP, false, true);

        assertThat(rules.editable()).isTrue();
        assertThat(rules.hostRequired()).isTrue();
        assertThat(rules.replayFileRequired()).isFalse();
        assertThat(rules.restPortEditable()).isFalse();
        assertThat(rules.serverText()).isEqualTo("Server: off");
        assertThat(rules.recordText()).isEqualTo("Start recording");
        assertThat(rules.recordEnabled()).isTrue();
        assertThat(rules.recordHint()).isEmpty();
    }

    @Test
    void aFileReplayRequiresTheFileAndAnEnabledServerItsPort() {
        SettingsFormRules rules = SettingsFormRules.of(TCP.withConnectionType(ConnectionType.FILE).withReplayFile("x.timing").withRest(1999, true), false, true);

        assertThat(rules.hostRequired()).isFalse();
        assertThat(rules.replayFileRequired()).isTrue();
        assertThat(rules.restPortEditable()).isTrue();
        assertThat(rules.serverText()).isEqualTo("Server: on");
    }

    @Test
    void invalidSettingsCannotBeRecordedAndNameTheFirstProblem() {
        SettingsFormRules rules = SettingsFormRules.of(TCP.withTcp("", 8585), false, true);

        assertThat(rules.recordEnabled()).isFalse();
        assertThat(rules.recordHint()).contains("TCP host must not be empty");
    }

    @Test
    void whileRecordingNothingIsEditableAndTheRecordingCanAlwaysBeStopped() {
        SettingsFormRules rules = SettingsFormRules.of(TCP.withTcp("", 8585).withRest(1999, true), true, true);

        assertThat(rules.editable()).isFalse();
        assertThat(rules.restPortEditable()).isFalse();
        assertThat(rules.recordText()).isEqualTo("Stop recording");
        assertThat(rules.recordEnabled()).isTrue();
        assertThat(rules.recordHint()).isEmpty();
    }

    @Test
    void withoutADocumentNothingCanBeRecorded() {
        assertThat(SettingsFormRules.of(TCP, false, false).recordEnabled()).isFalse();
    }
}
