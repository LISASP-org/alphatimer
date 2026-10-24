package org.lisasp.alphatiming.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

/**
 * One heading row of the Live View: the title left and the text right in bold, the middle text plain and centered on
 * the whole row (a grid could only center it between columns of different widths).
 */
final class HeadingLine extends AbstractComponent<HeadingLine> {

    private static final int GAP = 2;

    private String left = "";
    private String middle = "";
    private String right = "";

    synchronized void setTexts(String left, String middle, String right) {
        this.left = left;
        this.middle = middle;
        this.right = right;
        invalidate();
    }

    synchronized void setRight(String right) {
        this.right = right;
        invalidate();
    }

    @Override
    protected ComponentRenderer<HeadingLine> createDefaultRenderer() {
        return new ComponentRenderer<>() {
            @Override
            public TerminalSize getPreferredSize(HeadingLine line) {
                synchronized (line) {
                    int outer = Math.max(width(line.left), width(line.right));
                    return new TerminalSize(2 * outer + width(line.middle) + 2 * GAP, 1);
                }
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, HeadingLine line) {
                synchronized (line) {
                    int columns = graphics.getSize().getColumns();
                    graphics.applyThemeStyle(line.getThemeDefinition().getNormal());
                    graphics.fill(' ');
                    graphics.putString((columns - width(line.middle)) / 2, 0, line.middle);
                    graphics.enableModifiers(SGR.BOLD);
                    graphics.putString(0, 0, line.left);
                    graphics.putString(columns - width(line.right), 0, line.right);
                }
            }
        };
    }

    private static int width(String text) {
        return TerminalTextUtils.getColumnWidth(text);
    }
}
