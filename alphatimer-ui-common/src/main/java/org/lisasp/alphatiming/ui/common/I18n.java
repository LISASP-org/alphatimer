package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.ui.core.recording.RecordedData;
import org.lisasp.alphatiming.ui.core.recording.RecordingError;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.SerialMode;
import org.lisasp.alphatiming.ui.core.settings.SettingsProblem;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/** Looks up user-visible texts in {@code messages*.properties}; English is the base, German is localized. */
public final class I18n {

    private static final String BUNDLE = "org.lisasp.alphatiming.ui.common.messages";

    private I18n() {
    }

    public static String describe(SettingsProblem problem) {
        return get("problem." + problem.name());
    }

    public static String describe(RecordingError error) {
        if (error.kind() == RecordingError.Kind.INVALID_SETTINGS) {
            return error.problems().stream().map(I18n::describe).collect(Collectors.joining("; "));
        }
        return get("error." + error.kind().name(), error.detail());
    }

    public static String describe(RecordedData data) {
        return get("data." + data.name());
    }

    public static String label(ConnectionType type) {
        return get("input.type." + type.name());
    }

    public static String label(SerialMode mode) {
        return get("mode." + mode.name());
    }

    public static String get(String key, Object... args) {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, Locale.getDefault());
        String text = bundle.containsKey(key) ? bundle.getString(key) : key;
        return args.length == 0 ? text : MessageFormat.format(text, args);
    }
}
