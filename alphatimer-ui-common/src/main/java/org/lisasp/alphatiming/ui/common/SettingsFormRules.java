package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.Input;
import org.lisasp.alphatiming.ui.core.settings.Settings;
import org.lisasp.alphatiming.ui.core.settings.SettingsProblem;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Which settings fields a front end offers for the entered settings, and whether they can be recorded.
 *
 * @param connectionTypes the connection types offered: once the project holds data, only those of its input
 */
public record SettingsFormRules(boolean editable,
                                boolean hostRequired,
                                boolean replayFileRequired,
                                boolean lenexFileRequired,
                                List<ConnectionType> connectionTypes,
                                boolean restPortEditable,
                                String serverText,
                                String recordText,
                                boolean recordEnabled,
                                Optional<String> recordHint) {

    public static SettingsFormRules of(Settings fields, boolean running, boolean hasDocument) {
        return of(fields, running, hasDocument, Optional.empty());
    }

    public static SettingsFormRules of(Settings fields, boolean running, boolean hasDocument, Optional<Input> recorded) {
        List<SettingsProblem> problems = fields.validate();
        return new SettingsFormRules(!running,
                                     fields.connectionType() == ConnectionType.TCP,
                                     fields.connectionType() == ConnectionType.FILE,
                                     fields.connectionType() == ConnectionType.LENEX,
                                     Arrays.stream(ConnectionType.values()).filter(t -> recorded.map(i -> t.input() == i).orElse(true)).toList(),
                                     !running && fields.restEnabled(),
                                     fields.restEnabled() ? I18n.get("general.server.on") : I18n.get("general.server.off"),
                                     running ? I18n.get("recording.stop") : I18n.get("recording.start"),
                                     running || (problems.isEmpty() && hasDocument),
                                     !running && !problems.isEmpty() ? Optional.of(I18n.describe(problems.get(0))) : Optional.empty());
    }
}
