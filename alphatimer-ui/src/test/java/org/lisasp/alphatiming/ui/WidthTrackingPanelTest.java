package org.lisasp.alphatiming.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WidthTrackingPanelTest {

    @Test
    void followsTheWidthOfTheViewportInsteadOfItsOwnPreferredWidth() {
        WidthTrackingPanel panel = new WidthTrackingPanel();
        assertThat(panel.getScrollableTracksViewportWidth()).isTrue();
        assertThat(panel.getScrollableTracksViewportHeight()).isFalse();
    }
}
