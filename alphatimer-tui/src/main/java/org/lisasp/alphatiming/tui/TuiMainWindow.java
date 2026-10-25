package org.lisasp.alphatiming.tui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.gui2.menu.Menu;
import com.googlecode.lanterna.gui2.menu.MenuBar;
import com.googlecode.lanterna.gui2.menu.MenuItem;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import org.lisasp.alphatiming.ui.common.AppController;
import org.lisasp.alphatiming.ui.common.AppStatus;
import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.common.TimeFormat;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** The layout of the GUI window in text: menu bar, the two panels selectable on the left, status line at the bottom. */
final class TuiMainWindow extends BasicWindow {

    private final AppController controller;
    private final TuiSavePrompt prompt;
    private final WindowBasedTextGUI gui;

    private final TuiSettingsPanel settingsPanel;
    private final TuiLiveViewPanel liveViewPanel = new TuiLiveViewPanel();
    private final Panel content = new Panel(new LinearLayout(Direction.VERTICAL));
    private final Component settingsCard;
    private final Component liveCard;
    private final Label pingMarker = new Label("\u25CF").setForegroundColor(TextColor.ANSI.GREEN);
    private final Label statusState = new Label("");
    private final Label statusWarning = new Label("").setForegroundColor(TextColor.ANSI.RED_BRIGHT);
    private final Label statusLastMessage = new Label("");
    private final Label statusFile = new Label("");
    private final MenuItem startItem;
    private final MenuItem stopItem;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "tui-refresh");
        t.setDaemon(true);
        return t;
    });

    private boolean pingBright;

    TuiMainWindow(AppController controller, TuiSavePrompt prompt, WindowBasedTextGUI gui) {
        this.controller = controller;
        this.prompt = prompt;
        this.gui = gui;
        this.settingsPanel = new TuiSettingsPanel(controller, gui);
        setHints(List.of(Hint.FULL_SCREEN, Hint.NO_DECORATIONS));

        settingsCard = settingsPanel.withBorder(Borders.singleLine(I18n.get("nav.settings")));
        liveCard = liveViewPanel.withBorder(Borders.singleLine(I18n.get("nav.live")));
        startItem = new MenuItem(I18n.get("menu.start"), settingsPanel::toggleRecording);
        stopItem = new MenuItem(I18n.get("menu.stop"), controller::stopRecording);
        setMenuBar(createMenuBar());

        Panel body = new Panel(new BorderLayout());
        body.addComponent(createNavigation(), BorderLayout.Location.LEFT);
        body.addComponent(content, BorderLayout.Location.CENTER);
        body.addComponent(createStatusLine(), BorderLayout.Location.BOTTOM);
        setComponent(body);
        showSettings();

        controller.addChangeListener(() -> later(this::refresh));
        controller.addStateListener(state -> later(this::refresh));
        controller.addPingListener(() -> later(this::ping));
        controller.addHeatListener(heats -> later(() -> liveViewPanel.setHeats(heats)));
        controller.addMessageListener(text -> later(() -> {
            settingsPanel.appendMessage(text);
            statusLastMessage.setText(I18n.get("status.lastMessage", TimeFormat.clock(LocalTime.now())));
        }));
        liveViewPanel.setHeats(controller.liveHeats());
        addWindowListener(new WindowListenerAdapter() {
            /** Before the focused component: a text box would type the letter of Ctrl+N/O/C. */
            @Override
            public void onInput(Window window, KeyStroke key, AtomicBoolean deliverEvent) {
                if (handleShortcut(key)) {
                    deliverEvent.set(false);
                }
            }
        });

        timer.scheduleWithFixedDelay(() -> later(liveViewPanel::updateClock), 200, 200, TimeUnit.MILLISECONDS);
        timer.scheduleWithFixedDelay(() -> later(this::refresh), 1, 1, TimeUnit.SECONDS);
        refresh();
    }

    private void later(Runnable action) {
        gui.getGUIThread().invokeLater(action);
    }

    private MenuBar createMenuBar() {
        Menu file = new Menu(I18n.get("menu.file"));
        file.add(new MenuItem(I18n.get("menu.new"), controller::newProject));
        file.add(new MenuItem(I18n.get("menu.open"), this::open));
        file.add(new MenuItem(I18n.get("menu.save"), controller::save));
        file.add(new MenuItem(I18n.get("menu.saveAs"), controller::saveAs));
        file.add(new MenuItem(I18n.get("menu.exit"), this::exit));
        Menu recording = new Menu(I18n.get("menu.recording"));
        recording.add(startItem);
        recording.add(stopItem);
        return new MenuBar().add(file).add(recording);
    }

    private Panel createNavigation() {
        Panel navigation = new Panel(new LinearLayout(Direction.VERTICAL));
        navigation.addComponent(new Button(I18n.get("nav.settings"), this::showSettings), LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        navigation.addComponent(new Button(I18n.get("nav.live"), this::showLiveView), LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        navigation.addComponent(new EmptySpace());
        navigation.addComponent(new Label(I18n.get("tui.keys")));
        return navigation;
    }

    private Panel createStatusLine() {
        Panel status = new Panel(new LinearLayout(Direction.HORIZONTAL).setSpacing(2));
        status.addComponent(pingMarker);
        status.addComponent(statusState);
        status.addComponent(statusWarning);
        status.addComponent(statusLastMessage);
        status.addComponent(statusFile);
        return status;
    }

    void showSettings() {
        content.removeAllComponents();
        content.addComponent(settingsCard, LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow));
    }

    void showLiveView() {
        content.removeAllComponents();
        content.addComponent(liveCard, LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow));
    }

    /**
     * Ctrl+S and Ctrl+Q are left alone: most terminals use them for flow control and never pass them on. The text of the
     * focused field is committed first, as leaving the field would.
     */
    private boolean handleShortcut(KeyStroke key) {
        switch (key.getKeyType()) {
            case F2 -> {
                settingsPanel.commit();
                controller.save();
            }
            case F5 -> showSettings();
            case F6 -> showLiveView();
            case F10 -> exit();
            case Character -> {
                if (!key.isCtrlDown()) {
                    return false;
                }
                switch (Character.toLowerCase(key.getCharacter())) {
                    case 'n' -> {
                        settingsPanel.commit();
                        controller.newProject();
                    }
                    case 'o' -> open();
                    case 'c' -> exit();
                    default -> {
                        return false;
                    }
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void open() {
        settingsPanel.commit();
        prompt.askOpenPath().ifPresent(controller::open);
    }

    void exit() {
        settingsPanel.commit();
        if (controller.requestClose()) {
            close();
        }
    }

    @Override
    public void close() {
        timer.shutdownNow();
        super.close();
    }

    private void ping() {
        pingBright = !pingBright;
        pingMarker.setForegroundColor(pingBright ? TextColor.ANSI.GREEN_BRIGHT : TextColor.ANSI.GREEN);
    }

    private void refresh() {
        AppStatus status = AppStatus.of(controller);
        setTitle(status.title());
        statusState.setText(status.state());
        statusWarning.setText(status.warning());
        statusFile.setText(status.file());
        startItem.setEnabled(status.canStart());
        stopItem.setEnabled(status.canStop());
        if (controller.document() != null) {
            settingsPanel.refresh();
        }
        liveViewPanel.setRecording(status.recording());
    }

    TuiSettingsPanel settingsPanel() {
        return settingsPanel;
    }

    TuiLiveViewPanel liveViewPanel() {
        return liveViewPanel;
    }
}
