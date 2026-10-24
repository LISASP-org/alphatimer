package org.lisasp.alphatiming.ui;

import javax.swing.UIManager;
import java.awt.Font;

/** The fonts of the live view in the normal UI size: times in a monospaced font, lap texts and heat headings in the UI font. */
final class LiveFonts {

    private LiveFonts() {
    }

    static Font time() {
        return monospaced(Font.PLAIN);
    }

    static Font finalTime() {
        return monospaced(Font.BOLD);
    }

    static Font clock() {
        return monospaced(Font.BOLD);
    }

    static Font laneNumber() {
        return uiFont().deriveFont(Font.BOLD);
    }

    static Font lapText() {
        return uiFont().deriveFont(Font.PLAIN);
    }

    static Font heading() {
        return uiFont().deriveFont(Font.BOLD);
    }

    static Font times(String text) {
        return plainShowing(uiFont(), text);
    }

    /**
     * The UI font is a fixed font family (set by DesignInit), and Swing takes no missing characters from other fonts for
     * it; the logical Dialog font does, so it stands in when a character such as the arrow is missing.
     */
    static Font plainShowing(Font font, String text) {
        if (font.canDisplayUpTo(text) == -1) {
            return font.isPlain() ? font : font.deriveFont(Font.PLAIN);
        }
        return new Font(Font.DIALOG, Font.PLAIN, 1).deriveFont(font.getSize2D());
    }

    private static Font monospaced(int style) {
        return new Font(Font.MONOSPACED, style, 1).deriveFont(uiFont().getSize2D());
    }

    private static Font uiFont() {
        return UIManager.getFont("Label.font");
    }
}
