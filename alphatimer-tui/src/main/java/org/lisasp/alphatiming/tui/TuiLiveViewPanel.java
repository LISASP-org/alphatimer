package org.lisasp.alphatiming.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.LiveHeats;
import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.common.LaneRows;
import org.lisasp.alphatiming.ui.common.LiveTexts;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** The "Live View" panel of the GUI: the live heat with its running time, below it the heat that was active before. */
final class TuiLiveViewPanel extends Panel {

    private static final TextColor[] PODIUM = {TextColor.ANSI.YELLOW_BRIGHT, TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.RED};

    private final HeadingLine heading = new HeadingLine();
    private final Panel liveRows = new Panel(new GridLayout(4));
    private final HeadingLine previousHeading = new HeadingLine();
    private final Panel previousRows = new Panel(new GridLayout(4));
    private final Panel previousSection = new Panel(new LinearLayout(Direction.VERTICAL));

    private HeatDto heat;
    private boolean recording;

    TuiLiveViewPanel() {
        super(new LinearLayout(Direction.VERTICAL));
        heading.setTexts(LiveTexts.title(null), "", LiveTexts.clock(null, LocalDateTime.now(), false));
        addComponent(heading, LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        addComponent(liveRows);

        previousSection.addComponent(new EmptySpace());
        previousSection.addComponent(previousHeading, LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        previousSection.addComponent(previousRows);
        previousSection.setVisible(false);
        addComponent(previousSection, LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
    }

    void setHeats(LiveHeats heats) {
        HeatDto current = heats == null ? null : heats.current();
        HeatDto previous = heats == null ? null : heats.previous();
        Set<Integer> usedLanes = heats == null ? Set.of() : heats.usedLanes();
        heat = current;
        heading.setTexts(LiveTexts.title(current), LiveTexts.times(current), LiveTexts.clock(current, LocalDateTime.now(), recording));
        fill(liveRows, LaneRows.from(current, usedLanes));
        fill(previousRows, LaneRows.from(previous, usedLanes));
        previousSection.setVisible(previous != null);
        previousHeading.setTexts(previous == null ? "" : LiveTexts.title(previous), LiveTexts.times(previous), I18n.get("live.previous"));
        updateClock();
    }

    void setRecording(boolean recording) {
        this.recording = recording;
        updateClock();
    }

    void updateClock() {
        heading.setRight(LiveTexts.clock(heat, LocalDateTime.now(), recording));
    }

    private static void fill(Panel panel, List<LaneRows.Row> rows) {
        panel.removeAllComponents();
        for (LaneRows.Row row : rows) {
            panel.addComponent(new Label(String.format("%3d", row.lane())).addStyle(SGR.BOLD));
            panel.addComponent(new Label(row.detail()), GridLayout.createHorizontallyFilledLayoutData());
            Label time = new Label(String.format("%10s", row.time()));
            if (row.finished()) {
                time.addStyle(SGR.BOLD);
            }
            panel.addComponent(time);
            Label place = new Label(row.place() > 0 ? "#" + row.place() : "   ");
            if (row.place() > 0 && row.place() <= PODIUM.length) {
                place.setForegroundColor(PODIUM[row.place() - 1]);
            }
            panel.addComponent(place);
        }
    }
}
