package org.lisasp.alphatiming.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolTipsTest {

    @Test
    void noToolTipForAnEmptyText() {
        assertThat(ToolTips.of("")).isNull();
    }

    @Test
    void linesAreKeptAndMarkupIsEscaped() {
        assertThat(ToolTips.of("a <b>\nc & d")).isEqualTo("<html>a &lt;b&gt;<br>c &amp; d</html>");
    }

    @Test
    void leadingSpacesAreKept() {
        assertThat(ToolTips.of("Add it with:\n    sudo usermod")).isEqualTo("<html>Add it with:<br>&nbsp;&nbsp;&nbsp;&nbsp;sudo usermod</html>");
    }
}
