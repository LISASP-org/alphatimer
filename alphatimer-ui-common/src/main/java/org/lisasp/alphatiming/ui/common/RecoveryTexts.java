package org.lisasp.alphatiming.ui.common;

import org.lisasp.alphatiming.project.db.CrashLeftover;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

public final class RecoveryTexts {

    private RecoveryTexts() {
    }

    public static String question(CrashLeftover leftover) {
        return I18n.get("dialog.recover.message", projectFile(leftover), lastModified(leftover));
    }

    public static String discardQuestion(CrashLeftover leftover) {
        return I18n.get("dialog.discard.message", projectFile(leftover), lastModified(leftover));
    }

    private static String projectFile(CrashLeftover leftover) {
        return leftover.projectFile().map(Path::toString).orElseGet(() -> I18n.get("dialog.recover.noProjectFile"));
    }

    private static String lastModified(CrashLeftover leftover) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                                .withLocale(Locale.getDefault())
                                .withZone(ZoneId.systemDefault())
                                .format(leftover.lastModified());
    }
}
