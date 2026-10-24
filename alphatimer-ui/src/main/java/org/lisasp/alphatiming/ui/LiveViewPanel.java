package org.lisasp.alphatiming.ui;

import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.common.LiveTexts;
import org.lisasp.alphatiming.ui.common.LaneRows;
import org.lisasp.alphatiming.ui.common.TimeFormat;
import com.jgoodies.forms.factories.CC;
import com.jgoodies.forms.layout.FormLayout;
import de.df.jutils.gui.JGlassPanel;
import de.df.jutils.gui.border.BorderUtils;
import de.df.jutils.gui.layout.CenterLayout;
import de.df.jutils.gui.layout.FormLayoutUtils;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.LiveHeats;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The live heat with its running time on top and, below it, the last state of the heat that was active before.
 */
public final class LiveViewPanel extends JPanel {

    private static final String HEAT_GAP = "20dlu";
    /** Both outer columns grow alike, so the start and finish times stand in the middle of the heading. */
    private static final String HEADING_COLUMNS = "fill:0px:grow, 8dlu, center:default, 8dlu, fill:0px:grow";

    private final JLabel title = new JLabel(I18n.get("live.none"));
    private final JLabel clock = new JLabel(TimeFormat.running(0), SwingConstants.RIGHT);
    private final JPanel liveRows = new JPanel();
    private final JLabel times = new JLabel();
    private final JLabel previousTitle = new JLabel();
    private final JLabel previousTimes = new JLabel();
    private final JLabel previousMarker = new JLabel(I18n.get("live.previous"), SwingConstants.RIGHT);
    private final JPanel previousRows = new JPanel();
    private final JPanel previousSection = new JPanel(new FormLayout("fill:0px:grow", "fill:default, 4dlu, fill:default"));
    private final Timer clockTimer = new Timer(100, e -> updateClock());
    private final JLabel information = new JLabel(I18n.get("live.none"));
    private final JGlassPanel<JPanel> glass;

    private HeatDto heat;
    private boolean recording;
    private List<LaneRows.Row> displayed = List.of();
    private List<LaneRows.Row> displayedPrevious = List.of();

    public LiveViewPanel() {
        super(new BorderLayout());
        JPanel live = new JPanel(new FormLayout("fill:0px:grow", "fill:default, 8dlu, fill:default:grow"));

        title.setFont(LiveFonts.heading());
        clock.setFont(LiveFonts.clock());
        JPanel header = new JPanel(new FormLayout(HEADING_COLUMNS, "center:default"));
        header.add(title, CC.xy(1, 1));
        header.add(times, CC.xy(3, 1));
        header.add(clock, CC.xy(5, 1, "right, center"));
        live.add(header, CC.xy(1, 1));

        previousTitle.setFont(LiveFonts.heading());
        previousMarker.setFont(LiveFonts.heading());
        JPanel previousHeader = new JPanel(new FormLayout(HEADING_COLUMNS, "center:default"));
        previousHeader.add(previousTitle, CC.xy(1, 1));
        previousHeader.add(previousTimes, CC.xy(3, 1));
        previousHeader.add(previousMarker, CC.xy(5, 1, "right, center"));
        previousSection.add(previousHeader, CC.xy(1, 1));
        previousSection.add(previousRows, CC.xy(1, 3));
        previousSection.setVisible(false);

        JPanel content = new JPanel(new FormLayout("fill:0px:grow", "fill:default, " + HEAT_GAP + ", fill:default"));
        content.add(liveRows, CC.xy(1, 1));
        content.add(previousSection, CC.xy(1, 3));
        WidthTrackingPanel top = new WidthTrackingPanel();
        top.add(content, BorderLayout.NORTH);
        live.add(top, CC.xy(1, 3));

        glass = new JGlassPanel<>(live);
        JPanel info = new JPanel();
        info.setBorder(BorderUtils.createLabeledBorder(I18n.get("info.title")));
        information.setBorder(BorderUtils.createSpaceBorder());
        info.add(information);
        glass.getGlassPanel().setLayout(new CenterLayout());
        glass.getGlassPanel().add(info);
        glass.setEnabled(false);
        add(glass, BorderLayout.CENTER);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        clockTimer.start();
    }

    @Override
    public void removeNotify() {
        clockTimer.stop();
        super.removeNotify();
    }

    Timer clockTimer() {
        return clockTimer;
    }

    /**
     * The clock only runs while a recording is running. Call on the EDT.
     */
    public void setRecording(boolean recording) {
        this.recording = recording;
        updateClock();
    }

    String clockText() {
        return clock.getText();
    }

    /**
     * Shows the live heat and the previous heat (null parts are cleared). Call on the EDT.
     */
    public void setHeats(LiveHeats heats) {
        HeatDto current = heats == null ? null : heats.current();
        HeatDto previous = heats == null ? null : heats.previous();

        heat = current;
        glass.setEnabled(current != null);
        displayed = LaneRows.from(current, heats == null ? Set.of() : heats.usedLanes());
        fill(liveRows, displayed);
        title.setText(LiveTexts.title(current));
        showTimes(times, LiveTexts.times(current));

        displayedPrevious = LaneRows.from(previous, heats == null ? Set.of() : heats.usedLanes());
        fill(previousRows, displayedPrevious);
        previousSection.setVisible(previous != null);
        if (previous != null) {
            previousTitle.setText(LiveTexts.title(previous));
            showTimes(previousTimes, LiveTexts.times(previous));
        }
        revalidate();
        repaint();
        updateClock();
    }

    private static void showTimes(JLabel label, String text) {
        label.setFont(LiveFonts.times(text));
        label.setText(text);
    }

    private static void fill(JPanel panel, List<LaneRows.Row> rows) {
        panel.removeAll();
        panel.setLayout(new FormLayout("fill:0px:grow", FormLayoutUtils.createLayoutString(Math.max(rows.size(), 1), 4, 0)));
        for (int i = 0; i < rows.size(); i++) {
            panel.add(new LaneRowPanel(rows.get(i)), CC.xy(1, 2 + 2 * i));
        }
        panel.revalidate();
        panel.repaint();
    }

    private void updateClock() {
        clock.setText(LiveTexts.clock(heat, LocalDateTime.now(), recording));
    }

    List<Component> rowComponents() {
        List<Component> all = new ArrayList<>(Arrays.asList(liveRows.getComponents()));
        all.addAll(Arrays.asList(previousRows.getComponents()));
        return all;
    }

    List<LaneRows.Row> displayedRows() {
        return displayed;
    }

    List<LaneRows.Row> displayedPreviousRows() {
        return displayedPrevious;
    }

    boolean isPreviousShown() {
        return previousSection.isVisible();
    }

    boolean isInformationShown() {
        return !glass.isEnabled();
    }

    String informationText() {
        return information.getText();
    }

    String titleText() {
        return title.getText();
    }

    String previousTitleText() {
        return previousTitle.getText();
    }

    String previousMarkerText() {
        return previousMarker.getText();
    }

    JLabel previousTitleLabel() {
        return previousTitle;
    }

    JLabel previousMarkerLabel() {
        return previousMarker;
    }

    Font titleFont() {
        return title.getFont();
    }

    Font previousTitleFont() {
        return previousTitle.getFont();
    }

    Rectangle liveSectionBounds() {
        return liveRows.getBounds();
    }

    Rectangle previousSectionBounds() {
        return previousSection.getBounds();
    }

    JLabel clockLabel() {
        return clock;
    }

    String timesText() {
        return times.getText();
    }

    String previousTimesText() {
        return previousTimes.getText();
    }

    JLabel timesLabel() {
        return times;
    }

    JLabel previousTimesLabel() {
        return previousTimes;
    }
}
