package org.lisasp.alphatiming.ui;

import de.df.jutils.gui.border.ShadowBorder;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/** The scroll pane around the button bar, as in the plugin window of JAuswertung: a shadow border, no horizontal scrolling. */
final class SelectorScrollPane extends JScrollPane {

    SelectorScrollPane(JComponent buttonBar) {
        super(buttonBar, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        getVerticalScrollBar().setVisible(false);
        getVerticalScrollBar().setUnitIncrement(10);
        setBorder(new ShadowBorder());
        getVerticalScrollBar().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                relayout();
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                relayout();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        if (getVerticalScrollBar().isVisible()) {
            preferred.width += getVerticalScrollBar().getPreferredSize().width;
        }
        return preferred;
    }

    private void relayout() {
        Container parent = getParent();
        if (parent != null) {
            parent.invalidate();
            parent.repaint();
            if (parent instanceof JComponent component) {
                component.revalidate();
            }
        }
    }
}
