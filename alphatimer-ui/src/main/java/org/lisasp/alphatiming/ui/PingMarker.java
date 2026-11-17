package org.lisasp.alphatiming.ui;

import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.common.PingIndicator;
import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A small round marker: gray while not connected, dark green while connected without a recent sign of life, bright green
 * while the timekeeping system shows signs of life, white-green for a message.
 */
public final class PingMarker extends JComponent {

    public static final Color DARK_GREEN = new Color(0, 100, 0);
    public static final Color BRIGHT_GREEN = new Color(60, 230, 60);
    public static final Color MESSAGE_GREEN = new Color(215, 255, 215);
    private static final Color GRAY_ON_LIGHT = new Color(100, 100, 100);
    private static final Color GRAY_ON_DARK = new Color(150, 150, 150);

    private PingIndicator shown = PingIndicator.DISCONNECTED;

    public PingMarker() {
        setToolTipText(I18n.get("status.ping"));
        setOpaque(false);
    }

    public void show(PingIndicator indicator) {
        if (shown != indicator) {
            shown = indicator;
            repaint();
        }
    }

    public PingIndicator shown() {
        return shown;
    }

    public Color currentColor() {
        return switch (shown) {
            case DISCONNECTED -> notConnectedGray();
            case SILENT -> DARK_GREEN;
            case BRIGHT -> BRIGHT_GREEN;
            case MESSAGE -> MESSAGE_GREEN;
        };
    }

    /** A dark gray on a light background; on a dark one a lighter gray, so it does not vanish. */
    private static Color notConnectedGray() {
        Color background = UIManager.getColor("Panel.background");
        if (background == null) {
            return GRAY_ON_LIGHT;
        }
        double brightness = (0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue()) / 255;
        return brightness < 0.5 ? GRAY_ON_DARK : GRAY_ON_LIGHT;
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
