package org.lisasp.alphatiming.ui;

import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.image.MultiResolutionImage;

import static org.assertj.core.api.Assertions.assertThat;

class AppIconsTest {

    @Test
    void theButtonBarIconsAreLucideImagesWithLargerVariantsForDisplayScaling() {
        for (Icon icon : new Icon[]{AppIcons.settings(), AppIcons.liveView()}) {
            assertThat(icon.getIconWidth()).isEqualTo(64);
            assertThat(icon.getIconHeight()).isEqualTo(64);
            assertThat(((ImageIcon) icon).getImage()).isInstanceOf(MultiResolutionImage.class);
            assertThat(((MultiResolutionImage) ((ImageIcon) icon).getImage()).getResolutionVariants()).hasSize(2);
        }
    }

    @Test
    void theWindowGetsTheProgramIconInEverySize() {
        assertThat(AppIcons.application()).extracting(image -> image.getWidth(null)).containsExactly(16, 32, 48, 128, 256);
    }

    @Test
    void theButtonIconsAreSmall() {
        for (Icon icon : new Icon[]{AppIcons.reload(), AppIcons.info()}) {
            assertThat(icon.getIconWidth()).isEqualTo(16);
            assertThat(((MultiResolutionImage) ((ImageIcon) icon).getImage()).getResolutionVariants()).hasSize(3);
        }
    }
}
