package org.lisasp.alphatiming.ui;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Image;
import java.awt.image.BaseMultiResolutionImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** The Lucide icons of JAuswertung, in the dark variant when the look and feel is dark. */
public final class AppIcons {

    private static final int NAV_SIZE = 64;
    private static final int BUTTON_SIZE = 16;
    private static final int LARGEST_SIZE = 128;

    private AppIcons() {
    }

    public static Icon settings() {
        return load("sliders-horizontal", NAV_SIZE);
    }

    public static Icon liveView() {
        return load("timer", NAV_SIZE);
    }

    public static Icon reload() {
        return load("refresh-cw", BUTTON_SIZE);
    }

    /** The program icon for the window and the task bar, rendered from {@code src/icons}. */
    public static List<Image> application() {
        return List.of(16, 32, 48, 128, 256).stream().map(size -> read("icons/app/alphatiming-" + size + ".png")).toList();
    }

    public static Icon info() {
        return load("info", BUTTON_SIZE);
    }

    /** Larger sizes are added as resolution variants for screens with display scaling. */
    private static Icon load(String name, int size) {
        String variant = isDarkTheme() ? "lucide-dark" : "lucide";
        List<Image> images = new ArrayList<>();
        for (int s = size; s <= Math.min(4 * size, LARGEST_SIZE); s *= 2) {
            images.add(read("icons/" + s + "x" + s + "/" + variant + "/" + name + ".png"));
        }
        return new ImageIcon(new BaseMultiResolutionImage(images.toArray(new Image[0])));
    }

    private static Image read(String resource) {
        try (InputStream in = AppIcons.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing icon " + resource);
            }
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isDarkTheme() {
        Color background = UIManager.getColor("Panel.background");
        if (background == null) {
            return false;
        }
        double brightness = (0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue()) / 255;
        return brightness < 0.5;
    }
}
