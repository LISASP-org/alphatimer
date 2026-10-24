package org.lisasp.alphatiming.ui;

import org.lisasp.alphatiming.ui.common.I18n;
import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A small round marker that switches between dark and bright green with every ping of the device. */
public final class PingMarker extends JComponent {

    public static final Color DARK_GREEN = new Color(0, 100, 0);
    public static final Color BRIGHT_GREEN = new Color(60, 230, 60);

    private boolean bright;

    public PingMarker() {
        setToolTipText(I18n.get("status.ping"));
        setOpaque(false);
    }

    public void ping() {
        bright = !bright;
        repaint();
    }

    public boolean isBright() {
        return bright;
    }

    public Color currentColor() {
        return bright ? BRIGHT_GREEN : DARK_GREEN;
    }

    /** The marker is as high as the letters of the font it is used with. */
    @Override
    public Dimension getPreferredSize() {
        Font font = getFont() != null ? getFont() : UIManager.getFont("Label.font");
        int size = getFontMetrics(font).getAscent();
        return new Dimension(size, size);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(currentColor());
        int diameter = Math.min(getWidth(), getHeight());
        g2.fillOval((getWidth() - diameter) / 2, (getHeight() - diameter) / 2, diameter, diameter);
        g2.dispose();
    }
}
