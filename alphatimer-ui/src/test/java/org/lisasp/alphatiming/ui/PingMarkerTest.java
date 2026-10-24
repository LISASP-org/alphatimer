package org.lisasp.alphatiming.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PingMarkerTest {

    @Test
    void everyPingSwitchesBetweenDarkAndBrightGreen() {
        PingMarker marker = new PingMarker();
        assertThat(marker.isBright()).isFalse();
        assertThat(marker.currentColor()).isEqualTo(PingMarker.DARK_GREEN);

        marker.ping();
        assertThat(marker.isBright()).isTrue();
        assertThat(marker.currentColor()).isEqualTo(PingMarker.BRIGHT_GREEN);

        marker.ping();
        assertThat(marker.isBright()).isFalse();
        assertThat(marker.currentColor()).isEqualTo(PingMarker.DARK_GREEN);
    }

    @Test
    void theMarkerFitsTheFontItIsUsedWith() {
        java.awt.Font base = javax.swing.UIManager.getFont("Label.font");
        PingMarker small = new PingMarker();
        small.setFont(base.deriveFont(10f));
        PingMarker large = new PingMarker();
        large.setFont(base.deriveFont(40f));

        int smallSize = small.getPreferredSize().height;
        int largeSize = large.getPreferredSize().height;
        assertThat(smallSize).isPositive();
        assertThat(largeSize).isGreaterThan(smallSize * 2);
        assertThat(small.getPreferredSize().width).isEqualTo(smallSize);
        assertThat(smallSize).isLessThanOrEqualTo(small.getFontMetrics(small.getFont()).getHeight());
    }
}
