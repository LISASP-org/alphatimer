package org.lisasp.alphatiming.ui;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LiveFontsTest {

    private static final String TIMES = "12:16:05 → 12:17:10";

    @Test
    void aFontThatCanShowTheTextIsKept() {
        Font dialog = new Font(Font.DIALOG, Font.PLAIN, 13);

        assertThat(LiveFonts.plainShowing(dialog, TIMES)).isEqualTo(dialog);
    }

    @Test
    void aFontWithoutTheArrowIsReplacedByOneThatShowsItInTheSameSize() {
        Optional<Font> withoutArrow = Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts())
                                            .filter(f -> f.canDisplay('A') && !f.canDisplay('→'))
                                            .findFirst();
        assumeTrue(withoutArrow.isPresent(), "every installed font has the arrow");
        Font font = withoutArrow.get().deriveFont(Font.BOLD, 15f);

        Font shown = LiveFonts.plainShowing(font, TIMES);

        assertThat(shown.canDisplayUpTo(TIMES)).isEqualTo(-1);
        assertThat(shown.getSize2D()).isEqualTo(15f);
        assertThat(shown.isPlain()).isTrue();
    }
}
