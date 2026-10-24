package org.lisasp.alphatiming.ui;

import org.lisasp.alphatiming.ui.common.LaneRows;
import com.jgoodies.forms.factories.CC;
import com.jgoodies.forms.factories.Paddings;
import com.jgoodies.forms.layout.FormLayout;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** One lane in the Live View: lane number, lap or penalty, time and a place badge. */
final class LaneRowPanel extends JPanel {

    private static final Color[] PODIUM = {new Color(212, 175, 55), new Color(160, 160, 168), new Color(176, 112, 64)};

    private final LaneRows.Row row;
    private final JLabel detail;
    private final JLabel time;
    private final PlaceBadge placeBadge;

    LaneRowPanel(LaneRows.Row row) {
        super(new FormLayout("fill:max(default;16dlu), 4dlu, fill:0px:grow, 4dlu, fill:max(default;50dlu), 4dlu, fill:default", "fill:default"));
        this.row = row;
        Color border = UIManager.getColor("Separator.foreground") == null ? Color.LIGHT_GRAY : UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border, 1, true), Paddings.createPadding("2dlu, 4dlu, 2dlu, 4dlu")));
        setOpaque(false);

        JLabel lane = label(String.valueOf(row.lane()), LiveFonts.laneNumber());
        detail = label(row.detail(), LiveFonts.lapText());
        time = label(row.time(), row.finished() ? LiveFonts.finalTime() : LiveFonts.time());
        time.setHorizontalAlignment(SwingConstants.RIGHT);

        add(lane, CC.xy(1, 1, "left,center"));
        add(detail, CC.xy(3, 1, "left,center"));
        add(time, CC.xy(5, 1, "right,center"));
        placeBadge = new PlaceBadge(row.place());
        add(placeBadge, CC.xy(7, 1, "fill,fill"));
    }

    Font detailFont() {
        return detail.getFont();
    }

    Font timeFont() {
        return time.getFont();
    }

    JComponent placeBadge() {
        return placeBadge;
    }

    LaneRows.Row row() {
        return row;
    }

    private static JLabel label(String text, Font font) {
        JLabel label = new JLabel(text.isEmpty() ? " " : text);
        label.setFont(font);
        return label;
    }

    private static final class PlaceBadge extends JComponent {

        private static final String WIDEST_PLACE = "10";

        private final int place;

        PlaceBadge(int place) {
            this.place = place;
            setFont(LiveFonts.laneNumber());
        }

        @Override
        public Dimension getPreferredSize() {
            int diameter = diameter();
            return new Dimension(diameter, diameter);
        }

        private int diameter() {
            FontMetrics metrics = getFontMetrics(getFont());
            return Math.max(metrics.stringWidth(WIDEST_PLACE), metrics.getHeight()) + metrics.getHeight() / 2;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (place <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(place <= PODIUM.length ? PODIUM[place - 1] : new Color(120, 124, 132));
            int diameter = Math.min(diameter(), Math.min(getWidth(), getHeight()));
            g2.fillOval((getWidth() - diameter) / 2, (getHeight() - diameter) / 2, diameter, diameter);
            g2.setColor(Color.WHITE);
            g2.setFont(getFont());
            FontMetrics metrics = g2.getFontMetrics();
            String text = String.valueOf(place);
            int x = (getWidth() - metrics.stringWidth(text)) / 2;
            int y = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }
}
