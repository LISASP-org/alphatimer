package org.lisasp.alphatiming.ui;

import de.df.jutils.gui.JButtonBar;
import de.df.jutils.gui.border.ShadowBorder;
import org.junit.jupiter.api.Test;

import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

import static org.assertj.core.api.Assertions.assertThat;

class SelectorScrollPaneTest {

    @Test
    void wrapsTheButtonBarLikeTheJAuswertungPluginWindow() {
        JButtonBar bar = new JButtonBar(SwingConstants.VERTICAL);
        bar.add(new JToggleButton("A"));
        SelectorScrollPane pane = new SelectorScrollPane(bar);

        assertThat(pane.getViewport().getView()).isSameAs(bar);
        assertThat(pane.getVerticalScrollBarPolicy()).isEqualTo(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        assertThat(pane.getHorizontalScrollBarPolicy()).isEqualTo(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        assertThat(pane.getBorder()).isInstanceOf(ShadowBorder.class);
    }

    @Test
    void reservesRoomForAVisibleScrollbar() {
        SelectorScrollPane pane = new SelectorScrollPane(new JButtonBar(SwingConstants.VERTICAL));
        int without = pane.getPreferredSize().width;
        pane.getVerticalScrollBar().setVisible(true);
        assertThat(pane.getPreferredSize().width).isGreaterThan(without);
    }
}
