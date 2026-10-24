package org.lisasp.alphatiming.ui;

import org.lisasp.alphatiming.ui.common.LaneRows;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LaneRowPanelTest {

    @Test
    void aRowKeepsItsHeightWhetherOrNotItShowsATimeLapOrPlace() {
        int empty = new LaneRowPanel(new LaneRows.Row(1, "", "", 0, false)).getPreferredSize().height;
        int withTime = new LaneRowPanel(new LaneRows.Row(1, "12.34", "Lap 1", 0, false)).getPreferredSize().height;
        int finished = new LaneRowPanel(new LaneRows.Row(1, "25.01", "", 1, true)).getPreferredSize().height;
        int penalty = new LaneRowPanel(new LaneRows.Row(1, "", "DNS", 0, false)).getPreferredSize().height;

        assertThat(empty).isPositive();
        assertThat(withTime).isEqualTo(empty);
        assertThat(finished).isEqualTo(empty);
        assertThat(penalty).isEqualTo(empty);
    }

    @Test
    void lapTextAndTimesUseTheNormalUiFontSize() {
        java.awt.Font ui = javax.swing.UIManager.getFont("Label.font");
        for (LaneRows.Row row : new LaneRows.Row[]{new LaneRows.Row(1, "12.34", "Zwischenzeit 1", 0, false), new LaneRows.Row(1, "25.01", "", 1, true)}) {
            LaneRowPanel panel = new LaneRowPanel(row);
            assertThat(panel.detailFont().getFamily()).isEqualTo(ui.getFamily());
            assertThat(panel.detailFont().getSize2D()).isEqualTo(ui.getSize2D());
            assertThat(panel.timeFont().getSize2D()).isEqualTo(ui.getSize2D());
            assertThat(panel.timeFont().getFamily()).as("the times stay monospaced").isNotEqualTo(ui.getFamily());
        }
    }

    @Test
    void thePlaceNumberUsesTheNormalFontAndTheCircleFitsAroundIt() {
        java.awt.Font ui = javax.swing.UIManager.getFont("Label.font");
        javax.swing.JComponent badge = new LaneRowPanel(new LaneRows.Row(1, "25.01", "", 10, true)).placeBadge();
        java.awt.FontMetrics metrics = badge.getFontMetrics(badge.getFont());

        assertThat(badge.getFont().getSize2D()).isEqualTo(ui.getSize2D());
        assertThat(badge.getPreferredSize().width).isGreaterThan(metrics.stringWidth("10"));
        assertThat(badge.getPreferredSize().height).isGreaterThan(metrics.getHeight());
    }
}
