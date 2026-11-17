package org.lisasp.alphatiming.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lisasp.alphatiming.ui.common.PingIndicator;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class PingMarkerTest {

    @Test
    void theMarkerStartsNotConnectedAndShowsEachStateInItsOwnColor() {
        PingMarker marker = new PingMarker();
        assertThat(marker.shown()).isEqualTo(PingIndicator.DISCONNECTED);
        Color notConnected = marker.currentColor();

        marker.show(PingIndicator.SILENT);
        assertThat(marker.currentColor()).isEqualTo(PingMarker.DARK_GREEN);

        marker.show(PingIndicator.BRIGHT);
        assertThat(marker.currentColor()).isEqualTo(PingMarker.BRIGHT_GREEN);

        marker.show(PingIndicator.MESSAGE);
        assertThat(marker.currentColor()).isEqualTo(PingMarker.MESSAGE_GREEN);

        marker.show(PingIndicator.DISCONNECTED);
        assertThat(marker.currentColor()).isEqualTo(notConnected).isNotIn(PingMarker.DARK_GREEN, PingMarker.BRIGHT_GREEN, PingMarker.MESSAGE_GREEN);
    }

    @ParameterizedTest
    @ValueSource(classes = {FlatLightLaf.class, FlatDarkLaf.class})
    void notConnectedIsAGrayThatStaysVisibleOnTheBackground(Class<? extends LookAndFeel> lookAndFeel) throws Exception {
        LookAndFeel before = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(lookAndFeel.getDeclaredConstructor().newInstance());
            Color gray = new PingMarker().currentColor();

            assertThat(gray.getGreen()).isEqualTo(gray.getRed());
            assertThat(gray.getBlue()).isEqualTo(gray.getRed());
            assertThat(contrast(gray, UIManager.getColor("Panel.background"))).isGreaterThanOrEqualTo(3.0);
        } finally {
            UIManager.setLookAndFeel(before);
        }
    }

    /** The contrast ratio of WCAG 2; 3:1 is its minimum for parts of a user interface. */
    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * linear(c.getRed()) + 0.7152 * linear(c.getGreen()) + 0.0722 * linear(c.getBlue());
    }

    private static double linear(int channel) {
        double v = channel / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
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
