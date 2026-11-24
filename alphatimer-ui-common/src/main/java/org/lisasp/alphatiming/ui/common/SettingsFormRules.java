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
 * @param systems the timing systems offered: once the project holds data, only the one of its data
 * @param sources the sources offered: those of the selected system
 * @param restPortEditable whether the server port can be changed: while the server is off, also during a recording
 * @param serverEditable whether the server can be switched: with a project, also while a recording runs
 */
public record SettingsFormRules(boolean editable,
                                boolean hostRequired,
                                boolean replayFileRequired,
                                boolean lenexFileRequired,
                                List<Input> systems,
                                List<ConnectionType> sources,
                                boolean restPortEditable,
                                boolean serverEditable,
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
                                     recorded.map(List::of).orElse(List.of(Input.values())),
                                     sourcesOf(fields.connectionType().input()),
                                     hasDocument && !fields.restEnabled(),
                                     hasDocument,
                                     fields.restEnabled() ? I18n.get("general.server.on") : I18n.get("general.server.off"),
                                     running ? I18n.get("recording.stop") : I18n.get("recording.start"),
                                     running || (problems.isEmpty() && hasDocument),
                                     !running && !problems.isEmpty() ? Optional.of(I18n.describe(problems.get(0))) : Optional.empty());
    }

    /** The source after the system was chosen: the current one if it belongs to the system, else the system's first. */
    public static ConnectionType sourceFor(Input system, ConnectionType current) {
        return current.input() == system ? current : sourcesOf(system).getFirst();
    }

    private static List<ConnectionType> sourcesOf(Input system) {
        return Arrays.stream(ConnectionType.values()).filter(t -> t.input() == system).toList();
    }
}
