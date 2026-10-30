package org.lisasp.alphatiming.ui;

import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.common.LaneRows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatiming.heats.HeatDto;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.LaneDto;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.LiveHeats;
import org.lisasp.alphatiming.heats.Penalty;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LiveViewPanelTest {

    private Locale original;

    @BeforeEach
    void useEnglish() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    private static void layoutAll(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) {
                layoutAll(nested);
            }
        }
    }

    private static HeatDto heat(int heatNumber, HeatStatus status, int timeInMillis, boolean finished) {
        return new HeatDto(3, heatNumber, status, null, null, 2, List.of(
                new LaneDto(1, timeInMillis, 2, LaneStatus.Used, Penalty.None, finished ? 1 : 0, finished),
                new LaneDto(2, 0, 0, LaneStatus.Used, Penalty.None, 0, false)));
    }

    @Test
    void showsTheLiveHeatOnTopAndThePreviousHeatBelowIt() {
        LiveViewPanel panel = new LiveViewPanel();
        panel.setHeats(new LiveHeats(heat(2, HeatStatus.Started, 12_340, false), heat(1, HeatStatus.Finished, 25_010, true)));

        assertThat(panel.titleText()).isEqualTo("Event 3 · Heat 2");
        assertThat(panel.displayedRows()).extracting(LaneRows.Row::time).containsExactly("12.34", "");
        assertThat(panel.isPreviousShown()).isTrue();
        assertThat(panel.previousTitleText()).isEqualTo("Event 3 · Heat 1");
        assertThat(panel.previousMarkerText()).isEqualTo("Previous heat");
        assertThat(panel.displayedPreviousRows()).extracting(LaneRows.Row::time).containsExactly("25.01", "");
        assertThat(panel.displayedPreviousRows().get(0).place()).isEqualTo(1);
    }

    @Test
    void theKnownHeatsAreListedInATableRightOfTheLiveHeat() {
        java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        HeatDto running = new HeatDto(3, 2, HeatStatus.Started, start.plusMinutes(3), null, 0, List.of());
        HeatDto finished = new HeatDto(3, 1, HeatStatus.Finished, start, start.plusSeconds(65), 0, List.of());
        HeatDto open = new HeatDto(4, 1, HeatStatus.Open, null, null, 0, List.of());
        LiveViewPanel panel = new LiveViewPanel();
        panel.setHeats(new LiveHeats(running, finished, new java.util.TreeSet<>(), List.of(open, running, finished)));
        panel.setSize(1000, 900);
        layoutAll(panel);

        javax.swing.JTable table = panel.heatTable();
        assertThat(List.of(table.getColumnName(0), table.getColumnName(1), table.getColumnName(2))).containsExactly("Start", "Finish", "Heat");
        assertThat(table.getRowCount()).as("the heat without a start is not listed").isEqualTo(2);
        assertThat(List.of(table.getValueAt(0, 0), table.getValueAt(0, 1), table.getValueAt(0, 2))).containsExactly("10:00:00", "10:01:05", "03-001");
        assertThat(List.of(table.getValueAt(1, 0), table.getValueAt(1, 1), table.getValueAt(1, 2))).containsExactly("10:03:00", "", "03-002");
        java.awt.Rectangle tableBounds = javax.swing.SwingUtilities.convertRectangle(table.getParent(), table.getBounds(), panel);
        assertThat(tableBounds.x).isGreaterThan((int) panel.liveAreaBounds().getMaxX());
    }

    @Test
    void theTableFollowsTheTimekeepingOffset() {
        java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        HeatDto finished = new HeatDto(3, 1, HeatStatus.Finished, start, start.plusSeconds(65), 0, List.of(), null, 7 * 3_600_000);
        LiveViewPanel panel = new LiveViewPanel();
        panel.setHeats(new LiveHeats(finished, null));

        panel.setTimekeepingOffset(2);

        assertThat(panel.heatTable().getValueAt(0, 0)).isEqualTo("09:00:00");
    }

    @Test
    void bothHeatsTellWhenTheyStartedAndTheFinishedOneWhenItFinished() {
        java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        HeatDto running = new HeatDto(3, 2, HeatStatus.Started, start.plusMinutes(3), null, 0, List.of());
        HeatDto finished = new HeatDto(3, 1, HeatStatus.Finished, start, start.plusSeconds(65), 0, List.of());
        LiveViewPanel panel = new LiveViewPanel();

        panel.setHeats(new LiveHeats(running, finished));

        assertThat(panel.timesText()).isEqualTo("10:03:00 \u2192");
        assertThat(panel.previousTimesText()).isEqualTo("10:00:00 \u2192 10:01:05");
    }

    @Test
    void theTimesStandInTheMiddleOfTheHeadingInThePlainNormalFont() {
        java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        LiveViewPanel panel = new LiveViewPanel();
        panel.setHeats(new LiveHeats(new HeatDto(3, 2, HeatStatus.Started, start.plusMinutes(3), null, 0, List.of()),
                                     new HeatDto(3, 1, HeatStatus.Finished, start, start.plusSeconds(65), 0, List.of())));
        panel.setSize(1000, 900);
        layoutAll(panel);

        for (javax.swing.JLabel times : List.of(panel.timesLabel(), panel.previousTimesLabel())) {
            java.awt.Rectangle bounds = javax.swing.SwingUtilities.convertRectangle(times.getParent(), times.getBounds(), panel);
            assertThat((double) bounds.x + bounds.width / 2.0).isCloseTo(panel.liveAreaBounds().getCenterX(), org.assertj.core.data.Offset.offset(1.0));
            assertThat(times.getFont().isPlain()).isTrue();
            assertThat(times.getFont().getSize()).isEqualTo(javax.swing.UIManager.getFont("Label.font").getSize());
        }
        java.awt.Rectangle title = javax.swing.SwingUtilities.convertRectangle(panel.previousTitleLabel().getParent(), panel.previousTitleLabel().getBounds(), panel);
        java.awt.Rectangle times = javax.swing.SwingUtilities.convertRectangle(panel.previousTimesLabel().getParent(), panel.previousTimesLabel().getBounds(), panel);
        assertThat(times.getCenterY()).isCloseTo(title.getCenterY(), org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void thePreviousHeatIsNamedOnTheLeftAndMarkedAsPreviousOnTheRight() {
        LiveViewPanel panel = new LiveViewPanel();
        panel.setHeats(new LiveHeats(heat(2, HeatStatus.Started, 12_340, false), heat(1, HeatStatus.Finished, 25_010, true)));
        panel.setSize(1000, 900);
        layoutAll(panel);

        java.awt.Rectangle title = javax.swing.SwingUtilities.convertRectangle(panel.previousTitleLabel().getParent(), panel.previousTitleLabel().getBounds(), panel);
        java.awt.Rectangle marker = javax.swing.SwingUtilities.convertRectangle(panel.previousMarkerLabel().getParent(), panel.previousMarkerLabel().getBounds(), panel);
        assertThat(title.x).isZero();
        assertThat(marker.x).isGreaterThan(title.x + panel.previousTitleLabel().getPreferredSize().width);
        assertThat(marker.x + marker.width).isEqualTo((int) panel.liveAreaBounds().getMaxX());
        assertThat(marker.y).isEqualTo(title.y);
    }

    @Test
    void aLaneUsedBeforeKeepsItsRowWithoutATimeInBothHeats() {
        LiveViewPanel panel = new LiveViewPanel();
        HeatDto live = new HeatDto(3, 2, HeatStatus.Started, null, null, 2, List.of(
                new LaneDto(1, 0, 0, LaneStatus.NotUsed, Penalty.None, 0, false),
                new LaneDto(2, 12_340, 1, LaneStatus.Used, Penalty.None, 0, false)));
        HeatDto previous = new HeatDto(3, 1, HeatStatus.Finished, null, null, 2, List.of(
                new LaneDto(1, 25_010, 2, LaneStatus.Used, Penalty.None, 1, true),
                new LaneDto(2, 25_980, 2, LaneStatus.Used, Penalty.None, 2, true)));
        panel.setHeats(new LiveHeats(live, previous, java.util.Set.of(1, 2)));

        assertThat(panel.displayedRows()).extracting(LaneRows.Row::lane).containsExactly(1, 2);
        assertThat(panel.displayedRows().get(0).time()).isEmpty();
        assertThat(panel.displayedPreviousRows()).extracting(LaneRows.Row::lane).containsExactly(1, 2);
    }

    @Test
    void bothHeadingsHaveTheSameFontSize() {
        LiveViewPanel panel = new LiveViewPanel();
        panel.setHeats(new LiveHeats(heat(2, HeatStatus.Started, 12_340, false), heat(1, HeatStatus.Finished, 25_010, true)));
        assertThat(panel.titleFont()).isEqualTo(panel.previousTitleFont());
    }

    @Test
    void bothHeadingsUseTheNormalUiFontInItsNormalSize() {
        LiveViewPanel panel = new LiveViewPanel();
        panel.setHeats(new LiveHeats(heat(2, HeatStatus.Started, 12_340, false), heat(1, HeatStatus.Finished, 25_010, true)));
        java.awt.Font ui = javax.swing.UIManager.getFont("Label.font");

        assertThat(panel.titleFont().getFamily()).isEqualTo(ui.getFamily());
        assertThat(panel.titleFont().getSize2D()).isEqualTo(ui.getSize2D());
        assertThat(panel.previousTitleFont().getFamily()).isEqualTo(ui.getFamily());
        assertThat(panel.previousTitleFont().getSize2D()).isEqualTo(ui.getSize2D());
    }

    @Test
    void theHeatsAreSeparatedByAGenerousGap() {
        LiveViewPanel panel = new LiveViewPanel();
        panel.setHeats(new LiveHeats(heat(2, HeatStatus.Started, 12_340, false), heat(1, HeatStatus.Finished, 25_010, true)));
        panel.setSize(1000, 900);
        layoutAll(panel);
        java.awt.Rectangle live = panel.liveSectionBounds();
        java.awt.Rectangle previous = panel.previousSectionBounds();
        java.awt.Component firstRow = panel.rowComponents().get(0);
        int gapBetweenRows = panel.rowComponents().get(1).getY() - (firstRow.getY() + firstRow.getHeight());
        assertThat(previous.y - (live.y + live.height)).isGreaterThan(3 * gapBetweenRows);
    }

    @Test
    void theSectionOfThePreviousHeatIsHiddenUntilThereIsOne() {
        LiveViewPanel panel = new LiveViewPanel();
        panel.setHeats(new LiveHeats(heat(1, HeatStatus.Started, 0, false), null));
        assertThat(panel.isPreviousShown()).isFalse();
        assertThat(panel.displayedPreviousRows()).isEmpty();
        assertThat(panel.displayedRows()).hasSize(2);
    }

    @Test
    void noHeatsClearsBothSections() {
        LiveViewPanel panel = new LiveViewPanel();
        panel.setHeats(new LiveHeats(heat(2, HeatStatus.Started, 12_340, false), heat(1, HeatStatus.Finished, 25_010, true)));
        panel.setHeats(LiveHeats.NONE);
        assertThat(panel.titleText()).isEqualTo(I18n.get("live.none"));
        assertThat(panel.displayedRows()).isEmpty();
        assertThat(panel.isPreviousShown()).isFalse();
    }

    @Test
    void anInformationOnAGlassPaneCoversTheViewWhileThereIsNoHeat() {
        LiveViewPanel panel = new LiveViewPanel();
        assertThat(panel.isInformationShown()).isTrue();
        assertThat(panel.informationText()).isEqualTo(I18n.get("live.none"));
        assertThat(javax.swing.SwingUtilities.isDescendingFrom(panel.heatTable(), panel.coveredByGlass())).as("the table of the heats is covered too").isTrue();

        panel.setHeats(new LiveHeats(heat(1, HeatStatus.Started, 0, false), null));
        assertThat(panel.isInformationShown()).isFalse();

        panel.setHeats(LiveHeats.NONE);
        assertThat(panel.isInformationShown()).isTrue();
    }

    @Test
    void theClockDoesNotRunOnFromAStaleReference() {
        java.time.LocalDateTime longAgo = java.time.LocalDateTime.now().minusHours(2);
        HeatDto stale = new HeatDto(3, 1, HeatStatus.Started, longAgo, null, 2, List.of(), longAgo.plusSeconds(25));

        LiveViewPanel panel = new LiveViewPanel();
        panel.setHeats(new LiveHeats(stale, null));
        assertThat(panel.clockText()).as("nothing is recording").isEqualTo("0:25.0");

        panel.setRecording(true);
        panel.setHeats(new LiveHeats(stale, null));
        assertThat(panel.clockText()).as("no message for hours").isEqualTo("0:25.0");
    }

    @Test
    void theClockRunsWhileRecordingAHeatWithRecentMessages() {
        java.time.LocalDateTime start = java.time.LocalDateTime.now().minusSeconds(20);
        HeatDto running = new HeatDto(3, 1, HeatStatus.Started, start, null, 2, List.of(), java.time.LocalDateTime.now().minusSeconds(2));
        LiveViewPanel panel = new LiveViewPanel();
        panel.setRecording(true);
        panel.setHeats(new LiveHeats(running, null));
        assertThat(panel.clockText()).startsWith("0:2");
    }

    @Test
    void longHeadingsAreCutInsteadOfPushingTheClockAndTheTimesOutOfView() {
        LiveViewPanel panel = new LiveViewPanel();
        panel.setHeats(new LiveHeats(heat(2, HeatStatus.Started, 12_340, false), heat(1, HeatStatus.Finished, 25_010, true)));
        panel.setSize(500, 900);
        layoutAll(panel);

        java.awt.Rectangle clock = javax.swing.SwingUtilities.convertRectangle(panel.clockLabel().getParent(), panel.clockLabel().getBounds(), panel);
        assertThat(clock.x + clock.width).isLessThanOrEqualTo(panel.getWidth());
        for (java.awt.Component row : panel.rowComponents()) {
            java.awt.Rectangle bounds = javax.swing.SwingUtilities.convertRectangle(row.getParent(), row.getBounds(), panel);
            assertThat(bounds.x + bounds.width).as("row of the lane in the live view").isLessThanOrEqualTo(panel.getWidth());
        }
    }
}
