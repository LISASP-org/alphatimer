package org.lisasp.alphatiming.ui;

import org.lisasp.alphatiming.ui.common.AppController;
import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.common.TimeFormat;
import org.lisasp.alphatiming.ui.common.AppStatus;
import com.jgoodies.forms.factories.CC;
import com.jgoodies.forms.factories.Paddings;
import com.jgoodies.forms.layout.FormLayout;
import de.df.jutils.gui.JButtonBar;
import de.df.jutils.gui.border.BorderUtils;
import de.df.jutils.gui.util.WindowUtils;
import org.lisasp.alphatiming.heats.LiveHeats;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;

/** JAuswertung-style layout: button bar on the left, the selected panel on the right, status bar at the bottom. */
public final class JAlphaTiming extends JFrame {

    static final String SETTINGS_CARD = "settings";
    static final String LIVE_CARD = "live";

    private final AppController controller;
    private final SwingSavePrompt prompt;

    private final JButtonBar buttonBar = new JButtonBar(SwingConstants.VERTICAL);
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final SettingsRecordingPanel settingsPanel;
    private final LiveViewPanel liveViewPanel = new LiveViewPanel();
    private final JLabel statusState = new JLabel();
    private final PingMarker pingMarker = new PingMarker();
    private final JLabel statusWarning = new JLabel();
    private final JLabel statusLastMessage = new JLabel(" ");
    private final JLabel statusFile = new JLabel();
    private final Timer refreshTimer = new Timer(1000, e -> refresh());
    private final JMenuItem startItem = new JMenuItem(I18n.get("menu.start"));
    private final JMenuItem stopItem = new JMenuItem(I18n.get("menu.stop"));

    public JAlphaTiming(AppController controller, SwingSavePrompt prompt) {
        super(I18n.get("app.title"));
        this.controller = controller;
        this.prompt = prompt;
        this.settingsPanel = new SettingsRecordingPanel(controller);
        prompt.setParent(this);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });

        settingsPanel.setBorder(BorderUtils.createLabeledBorder(I18n.get("nav.settings"), true));
        liveViewPanel.setBorder(BorderUtils.createLabeledBorder(I18n.get("nav.live"), true));
        content.add(settingsPanel, SETTINGS_CARD);
        content.add(liveViewPanel, LIVE_CARD);

        ButtonGroup group = new ButtonGroup();
        JToggleButton settingsButton = addButton(group, I18n.get("nav.settings"), AppIcons.settings(), SETTINGS_CARD);
        addButton(group, I18n.get("nav.live"), AppIcons.liveView(), LIVE_CARD);
        settingsButton.setSelected(true);

        add(createBody(), BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
        setJMenuBar(createMenuBar());

        controller.addChangeListener(() -> SwingUtilities.invokeLater(this::refresh));
        controller.addPingListener(() -> SwingUtilities.invokeLater(pingMarker::ping));
        controller.addHeatListener(h -> SwingUtilities.invokeLater(() -> liveViewPanel.setHeats(h)));
        controller.addStateListener(s -> SwingUtilities.invokeLater(this::refresh));
        controller.addMessageListener(text -> SwingUtilities.invokeLater(() -> {
            String now = TimeFormat.clock(LocalTime.now());
            settingsPanel.appendMessage(now + "  " + text);
            statusLastMessage.setText(I18n.get("status.lastMessage", now));
        }));
        liveViewPanel.setHeats(controller.liveHeats());

        refreshTimer.start();
        refresh();
        WindowUtils.setSize(this, 1200, 700);
        WindowUtils.center(this);
        setExtendedState(MAXIMIZED_BOTH);
    }

    private JPanel createBody() {
        FormLayout layout = new FormLayout("0dlu,fill:max(70dlu;default),4dlu,fill:0px:grow,0dlu", "0dlu,fill:0dlu:grow,0dlu");
        JPanel body = new JPanel(layout);
        body.setBorder(BorderUtils.createSpaceBorder());
        body.add(new SelectorScrollPane(buttonBar), CC.xy(2, 2));
        body.add(content, CC.xy(4, 2));
        return body;
    }

    private static Color separatorColor() {
        Color color = UIManager.getColor("Separator.foreground");
        return color == null ? Color.LIGHT_GRAY : color;
    }

    private JToggleButton addButton(ButtonGroup group, String text, Icon icon, String card) {
        JToggleButton button = new JToggleButton(text, icon);
        button.addActionListener(e -> cards.show(content, card));
        group.add(button);
        buttonBar.add(button);
        button.setBorder(BorderFactory.createCompoundBorder(button.getBorder(), Paddings.createPadding("4dlu, 4dlu, 4dlu, 4dlu")));
        return button;
    }

    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, separatorColor()), Paddings.createPadding("3dlu, 6dlu, 3dlu, 6dlu")));
        JPanel state = new JPanel(new FormLayout("fill:default, 4dlu, fill:default", "center:default"));
        state.setOpaque(false);
        state.add(pingMarker, CC.xy(1, 1));
        state.add(statusState, CC.xy(3, 1));
        bar.add(state, BorderLayout.WEST);
        JPanel middle = new JPanel(new FormLayout("fill:default, 8dlu, fill:0px:grow", "center:default"));
        middle.setOpaque(false);
        statusWarning.setForeground(Color.RED);
        statusWarning.setVisible(false);
        middle.add(statusWarning, CC.xy(1, 1));
        middle.add(statusLastMessage, CC.xy(3, 1));
        bar.add(middle, BorderLayout.CENTER);
        bar.add(statusFile, BorderLayout.EAST);
        return bar;
    }

    private JMenuBar createMenuBar() {
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenu file = new JMenu(I18n.get("menu.file"));
        file.add(item(I18n.get("menu.new"), KeyEvent.VK_N, shortcut, controller::newProject));
        file.add(item(I18n.get("menu.open"), KeyEvent.VK_O, shortcut, this::open));
        file.addSeparator();
        file.add(item(I18n.get("menu.save"), KeyEvent.VK_S, shortcut, controller::save));
        file.add(item(I18n.get("menu.saveAs"), 0, 0, controller::saveAs));
        file.addSeparator();
        file.add(item(I18n.get("menu.exit"), 0, 0, this::exit));

        JMenu recording = new JMenu(I18n.get("menu.recording"));
        startItem.addActionListener(e -> startRecordingFromMenu());
        stopItem.addActionListener(e -> controller.stopRecording());
        recording.add(startItem);
        recording.add(stopItem);

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(recording);
        return bar;
    }

    private static JMenuItem item(String text, int key, int modifiers, Runnable action) {
        JMenuItem item = new JMenuItem(new AbstractAction(text) {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
        if (key != 0) {
            item.setAccelerator(KeyStroke.getKeyStroke(key, modifiers));
        }
        return item;
    }

    void startRecordingFromMenu() {
        settingsPanel.startRecording();
    }

    private void open() {
        prompt.askOpenPath().ifPresent(controller::open);
    }

    private void exit() {
        if (controller.requestClose()) {
            dispose();
            System.exit(0);
        }
    }

    void refresh() {
        AppStatus status = AppStatus.of(controller);
        setTitle(status.title());
        statusFile.setText(status.file());
        statusWarning.setText(status.warning());
        statusWarning.setToolTipText(toolTip(status.warningDetails()));
        statusWarning.setVisible(!status.warning().isEmpty());
        statusState.setText(status.state());
        startItem.setEnabled(status.canStart());
        stopItem.setEnabled(status.canStop());
        settingsPanel.refresh();
        liveViewPanel.setRecording(status.recording());
    }

    @Override
    public void dispose() {
        refreshTimer.stop();
        super.dispose();
    }

    Timer refreshTimer() {
        return refreshTimer;
    }

    private static String toolTip(String details) {
        if (details.isEmpty()) {
            return null;
        }
        String escaped = details.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html>" + escaped.replace("\n", "<br>") + "</html>";
    }

    JLabel statusWarningLabel() {
        return statusWarning;
    }

    PingMarker pingMarker() {
        return pingMarker;
    }

    JLabel statusStateLabel() {
        return statusState;
    }

    JButtonBar buttonBar() {
        return buttonBar;
    }

    SettingsRecordingPanel settingsPanel() {
        return settingsPanel;
    }

    LiveViewPanel liveViewPanel() {
        return liveViewPanel;
    }

    void showHeats(LiveHeats heats) {
        liveViewPanel.setHeats(heats);
    }
}
