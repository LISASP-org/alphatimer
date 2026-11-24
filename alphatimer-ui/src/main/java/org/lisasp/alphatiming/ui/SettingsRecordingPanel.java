package org.lisasp.alphatiming.ui;

import org.lisasp.alphatiming.ui.common.AppController;
import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.common.SerialPortTexts;
import org.lisasp.alphatiming.ui.common.SettingsFormRules;
import org.lisasp.alphatiming.ui.common.ShownDocument;
import com.jgoodies.forms.factories.CC;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.RowSpec;
import de.df.jutils.gui.JWarningTextField;
import de.df.jutils.gui.border.BorderUtils;
import de.df.jutils.gui.layout.SimpleFormBuilder;
import org.lisasp.swing.filechooser.FileChooserUtils;
import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatiming.ui.core.project.TimingDocument;
import org.lisasp.alphatimer.serial.com.SerialPorts;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.Input;
import org.lisasp.alphatiming.ui.core.settings.SerialMode;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import java.util.ArrayList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;

@Slf4j
public final class SettingsRecordingPanel extends JPanel {

    private static final int MAX_LOG_LINES = 300;

    private final AppController controller;

    private final JComboBox<Input> system = new JComboBox<>(Input.values());
    private final JComboBox<ConnectionType> connectionType = new JComboBox<>(ConnectionType.values());
    private final JWarningTextField replayFile = new JWarningTextField(true, false);
    private final JButton chooseFile = new JButton(I18n.get("input.chooseFile"));
    private final JWarningTextField lenexFile = new JWarningTextField(true, false);
    private final JButton chooseLenexFile = new JButton(I18n.get("input.chooseFile"));
    private final JComboBox<Integer> lenexInterval = new JComboBox<>(Settings.LENEX_INTERVALS_SECONDS.toArray(Integer[]::new));
    private final JWarningTextField host = new JWarningTextField(true, false);
    private final JSpinner port = new JSpinner(new SpinnerNumberModel(8585, 1, 65535, 1));
    private final JComboBox<String> serialPort = new JComboBox<>();
    private final JButton refreshPorts = new JButton(AppIcons.reload());
    private final JTextArea portAccessHint = new JTextArea();
    private final JButton portAccessInfo = new JButton(AppIcons.info());
    /** The text column ignores the width of the wrapped text, which would otherwise keep the form from getting narrower. */
    private final JPanel portAccess = new JPanel(new FormLayout("default, 2dlu, fill:0px:grow", "center:default"));
    private String portAccessInfoText = "";
    private final JComboBox<SerialMode> serialMode = new JComboBox<>(SerialMode.values());
    private final JWarningTextField competitionKey = new JWarningTextField(true, false);
    private final JSpinner timekeepingOffset = new JSpinner(new SpinnerNumberModel(0, -Settings.MAX_TIMEKEEPING_OFFSET_HOURS, Settings.MAX_TIMEKEEPING_OFFSET_HOURS, 1));
    private final JSpinner autoSave = new JSpinner(new SpinnerNumberModel(60, 5, 3600, 5));
    private final JSpinner restPort = new JSpinner(new SpinnerNumberModel(Settings.DEFAULT_REST_PORT, 1, 65535, 1));
    private final JToggleButton serverToggle = new JToggleButton();
    private final JToggleButton recordToggle = new JToggleButton(I18n.get("recording.start"));
    private final JTextArea messageLog = new JTextArea();

    private final JPanel connectionCards = new JPanel(new CardLayout());
    private final ShownDocument shownDocument = new ShownDocument();

    private boolean updating;
    private boolean commitsHeld;
    private boolean portsLoaded;

    public SettingsRecordingPanel(AppController controller) {
        super(new FormLayout("fill:default:grow", "fill:default, 4dlu, fill:default:grow"));
        this.controller = controller;

        add(createForm(), CC.xy(1, 1));

        messageLog.setEditable(false);
        JScrollPane scroll = new JScrollPane(messageLog);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel messages = new JPanel(new BorderLayout());
        messages.setBorder(BorderUtils.createLabeledBorder(I18n.get("messages.title"), true));
        messages.add(scroll, BorderLayout.CENTER);
        add(messages, CC.xy(1, 3));

        serialPort.setEditable(true);
        // Stable names (/dev/serial/by-id/…) would otherwise make the form as wide as the longest of them.
        serialPort.setPrototypeDisplayValue("/dev/ttyUSB0");
        serialMode.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : modeLabel((SerialMode) value), index, selected, focus);
            }
        });

        connectionType.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : I18n.label((ConnectionType) value), index, selected, focus);
            }
        });
        connectionType.addActionListener(e -> connectionChanged());
        system.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : I18n.label((Input) value), index, selected, focus);
            }
        });
        system.addActionListener(e -> systemChanged());
        chooseFile.addActionListener(e -> chooseReplayFile());
        replayFile.addActionListener(e -> commit());
        chooseLenexFile.addActionListener(e -> chooseLenexFile());
        lenexFile.addActionListener(e -> commit());
        lenexInterval.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : I18n.get("input.lenexInterval.seconds", value), index, selected, focus);
            }
        });
        lenexInterval.addActionListener(e -> {
            if (!updating) {
                commit();
            }
        });
        refreshPorts.setToolTipText(I18n.get("input.refresh"));
        refreshPorts.addActionListener(e -> loadPorts());
        serialPort.addActionListener(e -> commit());
        serialMode.addActionListener(e -> commit());

        recordToggle.addActionListener(e -> {
            if (recordToggle.isSelected()) {
                startRecording();
            } else {
                controller.stopRecording();
            }
            refresh();
        });
        host.addActionListener(e -> commit());
        FocusAdapter commitOnFocusLost = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commit();
            }
        };
        host.addFocusListener(commitOnFocusLost);
        competitionKey.addActionListener(e -> commit());
        competitionKey.addFocusListener(commitOnFocusLost);
        serialPort.getEditor().getEditorComponent().addFocusListener(commitOnFocusLost);
        port.addChangeListener(e -> commit());
        timekeepingOffset.addChangeListener(e -> commit());
        autoSave.addChangeListener(e -> commit());
        restPort.addChangeListener(e -> commit());
        serverToggle.addActionListener(e -> {
            if (!updating) {
                controller.setServer(serverToggle.isSelected());
            }
        });

        refresh();
    }

    private static final String TCP_CARD = "tcp";
    private static final String SERIAL_CARD = "serial";
    private static final String FILE_CARD = "file";
    private static final String LENEX_CARD = "lenex";

    private JPanel createForm() {
        for (JSpinner spinner : new JSpinner[]{port, timekeepingOffset, autoSave, restPort}) {
            ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setHorizontalAlignment(JTextField.LEFT);
        }

        JPanel portChoice = new JPanel(new FormLayout("fill:default:grow, 4dlu, fill:default", "fill:default"));
        portChoice.setOpaque(false);
        portChoice.add(serialPort, CC.xy(1, 1));
        portChoice.add(refreshPorts, CC.xy(3, 1));
        portAccessHint.setEditable(false);
        portAccessHint.setFocusable(false);
        portAccessHint.setOpaque(false);
        portAccessHint.setLineWrap(true);
        portAccessHint.setWrapStyleWord(true);
        portAccessHint.setFont(UIManager.getFont("Label.font"));
        portAccessHint.addComponentListener(new ComponentAdapter() {
            /** A wrapped text knows its height only once it has its width, so the form is laid out again. */
            @Override
            public void componentResized(ComponentEvent e) {
                portAccessHint.revalidate();
            }
        });
        portAccessInfo.setBorder(BorderFactory.createEmptyBorder());
        portAccessInfo.setContentAreaFilled(false);
        portAccessInfo.addActionListener(e -> showPortAccessInfo());
        portAccess.setOpaque(false);
        portAccess.add(portAccessInfo, CC.xy(1, 1));
        portAccess.add(portAccessHint, CC.xy(3, 1, "fill, center"));
        portAccess.setVisible(false);

        SimpleFormBuilder typeForm = newBuilder();
        typeForm.add(I18n.get("input.system"), system);
        typeForm.add(I18n.get("input.type"), connectionType);
        SimpleFormBuilder tcpForm = newBuilder();
        tcpForm.add(I18n.get("input.tcpHost"), host);
        tcpForm.add(I18n.get("input.tcpPort"), port);
        SimpleFormBuilder serialForm = newBuilder();
        serialForm.add(I18n.get("input.serialPort"), portChoice);
        addBelowLastRow(serialForm, portAccess);
        serialForm.add(I18n.get("input.protocol"), serialMode);
        connectionCards.add(tcpForm.getPanel(), TCP_CARD);
        connectionCards.add(serialForm.getPanel(), SERIAL_CARD);
        JPanel fileChoice = new JPanel(new FormLayout("fill:default:grow, 4dlu, fill:default", "fill:default"));
        fileChoice.setOpaque(false);
        fileChoice.add(replayFile, CC.xy(1, 1));
        fileChoice.add(chooseFile, CC.xy(3, 1));
        SimpleFormBuilder fileForm = newBuilder();
        fileForm.add(I18n.get("input.replayFile"), fileChoice);
        connectionCards.add(fileForm.getPanel(), FILE_CARD);
        JPanel lenexChoice = new JPanel(new FormLayout("fill:default:grow, 4dlu, fill:default", "fill:default"));
        lenexChoice.setOpaque(false);
        lenexChoice.add(lenexFile, CC.xy(1, 1));
        lenexChoice.add(chooseLenexFile, CC.xy(3, 1));
        SimpleFormBuilder lenexForm = newBuilder();
        lenexForm.add(I18n.get("input.lenexFile"), lenexChoice);
        lenexForm.add(I18n.get("input.lenexInterval"), lenexInterval);
        connectionCards.add(lenexForm.getPanel(), LENEX_CARD);
        connectionCards.setOpaque(false);

        SimpleFormBuilder recordForm = newBuilder();
        recordForm.add((Component) null, recordToggle);

        JPanel input = new JPanel(new FormLayout("fill:default:grow", "fill:default, fill:default, 4dlu, fill:default"));
        input.setBorder(BorderUtils.createLabeledBorder(I18n.get("source.title"), true));
        input.add(typeForm.getPanel(), CC.xy(1, 1));
        input.add(connectionCards, CC.xy(1, 2));
        input.add(recordForm.getPanel(), CC.xy(1, 4));

        SimpleFormBuilder competitionForm = newBuilder();
        competitionForm.add(I18n.get("general.competitionKey"), competitionKey);
        competitionForm.add(I18n.get("general.timekeepingOffset"), timekeepingOffset);
        competitionForm.add(I18n.get("general.autoSave"), autoSave);
        JPanel competition = competitionForm.getPanel();
        competition.setBorder(BorderUtils.createLabeledBorder(I18n.get("settings.title"), true));

        SimpleFormBuilder serverForm = newBuilder();
        serverForm.add(I18n.get("general.serverPort"), restPort);
        serverForm.add((Component) null, serverToggle);
        JPanel server = serverForm.getPanel();
        server.setBorder(BorderUtils.createLabeledBorder(I18n.get("server.title"), true));

        JPanel general = new JPanel(new FormLayout("fill:default:grow", "fill:default, 4dlu, fill:default"));
        general.setOpaque(false);
        general.add(competition, CC.xy(1, 1));
        general.add(server, CC.xy(1, 3));

        FormLayout formLayout = new FormLayout("fill:default:grow, 4dlu, fill:default:grow", "fill:default");
        formLayout.setColumnGroups(new int[][]{{1, 3}});
        JPanel form = new JPanel(formLayout);
        form.add(input, CC.xy(1, 1));
        form.add(general, CC.xy(3, 1));
        return form;
    }

    /** The builder makes all its rows as high as the highest and puts a gap after each, so the row is added here: a hidden one takes no space. */
    private static void addBelowLastRow(SimpleFormBuilder form, Component component) {
        JPanel panel = form.getPanel();
        FormLayout layout = (FormLayout) panel.getLayout();
        int row = layout.getRowCount();
        layout.insertRow(row, RowSpec.decode("fill:default"));
        panel.add(component, CC.xyw(4, row, 2));
    }

    /** All forms get the same label column so that the fields of both groups start at the same position. */
    private static SimpleFormBuilder newBuilder() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        FormLayout layout = new FormLayout("0dlu, fill:max(default;110dlu), 4dlu, 16dlu, fill:0px:grow, 0dlu", "0dlu");
        return new SimpleFormBuilder(panel, layout, 4, false);
    }

    static String modeLabel(SerialMode mode) {
        return I18n.label(mode);
    }

    private ConnectionType selectedType() {
        return (ConnectionType) connectionType.getSelectedItem();
    }

    private Settings readFields() {
        Object selectedPort = serialPort.getEditor().getItem();
        return new Settings(selectedType(), host.getText().trim(), (Integer) port.getValue(), selectedPort == null ? "" : selectedPort.toString().trim(), (SerialMode) serialMode.getSelectedItem(), (Integer) autoSave.getValue(), (Integer) restPort.getValue(), serverToggle.isSelected(), replayFile.getText().trim(), competitionKey.getText().trim(), (Integer) timekeepingOffset.getValue(), lenexFile.getText().trim(), (Integer) lenexInterval.getSelectedItem());
    }

    private void chooseLenexFile() {
        String file = FileChooserUtils.openFile(SwingUtilities.getWindowAncestor(this));
        if (file != null) {
            lenexFile.setText(file);
            commit();
        }
    }

    private void chooseReplayFile() {
        String file = FileChooserUtils.openFile(SwingUtilities.getWindowAncestor(this));
        if (file != null) {
            replayFile.setText(file);
            commit();
        }
    }

    /** Another system starts with its first source; the form then offers the sources of that system. */
    private void systemChanged() {
        if (updating || system.getSelectedItem() == null) {
            return;
        }
        ConnectionType source = SettingsFormRules.sourceFor((Input) system.getSelectedItem(), selectedType());
        updating = true;
        try {
            connectionType.setModel(new DefaultComboBoxModel<>(new ConnectionType[]{source}));
            connectionType.setSelectedItem(source);
        } finally {
            updating = false;
        }
        connectionChanged();
    }

    private void connectionChanged() {
        if (updating) {
            return;
        }
        if (selectedType() == ConnectionType.SERIAL) {
            loadPorts();
        }
        commit();
    }

    private void loadPorts() {
        Object current = serialPort.getEditor().getItem();
        boolean wasUpdating = updating;
        updating = true;
        try {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            SerialPorts.list().forEach(model::addElement);
            serialPort.setModel(model);
            serialPort.setSelectedItem(current == null ? "" : current.toString());
            showPortAccess(SerialPortTexts.ofThisMachine());
            portsLoaded = true;
        } finally {
            updating = wasUpdating;
        }
    }

    private void showPortAccess(SerialPortTexts.Access access) {
        portAccessHint.setText(access.hint());
        portAccessInfoText = access.info();
        portAccessInfo.setToolTipText(ToolTips.of(access.info()));
        portAccess.setVisible(!access.hint().isEmpty());
    }

    private void showPortAccessInfo() {
        JTextArea info = new JTextArea(portAccessInfoText);
        info.setEditable(false);
        info.setOpaque(false);
        info.setFont(UIManager.getFont("Label.font"));
        JOptionPane.showMessageDialog(this, info, I18n.get("input.serialPort.infoTitle"), JOptionPane.INFORMATION_MESSAGE);
    }

    private void commit() {
        if (updating || commitsHeld) {
            return;
        }
        controller.applySettingsIfChanged(readFields());
        refreshButtons();
    }

    /** Applies what is typed to the document being left; until released, the fields must not reach its successor. */
    void holdCommits() {
        commit();
        commitsHeld = true;
    }

    void releaseCommits() {
        commitsHeld = false;
    }

    /** Applies whatever is typed in the fields (even if never committed) and starts recording. */
    void startRecording() {
        commit();
        controller.startRecording();
    }

    /** Brings the fields, buttons and labels in line with the document and the recording state. Call on the EDT. */
    public void refresh() {
        TimingDocument doc = controller.document();
        boolean replaced = shownDocument.replacedBy(doc);
        updating = true;
        try {
            if (doc != null) {
                Settings s = doc.settings();
                system.setSelectedItem(s.connectionType().input());
                connectionType.setSelectedItem(s.connectionType());
                if (replaced || !replayFile.hasFocus()) {
                    replayFile.setText(s.replayFile());
                }
                if (replaced || !lenexFile.hasFocus()) {
                    lenexFile.setText(s.lenexFile());
                }
                lenexInterval.setSelectedItem(s.lenexIntervalSeconds());
                if (replaced || !competitionKey.hasFocus()) {
                    competitionKey.setText(s.competitionKey());
                }
                if (replaced || !host.hasFocus()) {
                    host.setText(s.tcpHost());
                }
                port.setValue(clamp(s.tcpPort(), 1, 65535));
                timekeepingOffset.setValue(clamp(s.timekeepingOffsetHours(), -Settings.MAX_TIMEKEEPING_OFFSET_HOURS, Settings.MAX_TIMEKEEPING_OFFSET_HOURS));
                autoSave.setValue(clamp(s.autoSaveSeconds(), 5, 3600));
                restPort.setValue(clamp(s.restPort(), 1, 65535));
                serverToggle.setSelected(s.restEnabled());
                serialMode.setSelectedItem(s.serialMode());
                if (replaced || !serialPort.getEditor().getEditorComponent().hasFocus()) {
                    serialPort.setSelectedItem(s.serialPort());
                }
            }
        } finally {
            updating = false;
        }
        if (selectedType() == ConnectionType.SERIAL && !portsLoaded) {
            loadPorts();
        }
        refreshButtons();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void refreshButtons() {
        boolean running = controller.isRecording();
        Settings fields = readFields();
        SettingsFormRules rules = SettingsFormRules.of(fields, running, controller.document() != null, controller.recordedInput());
        offer(system, rules.systems());
        offer(connectionType, rules.sources());
        host.setRequired(rules.hostRequired());
        replayFile.setRequired(rules.replayFileRequired());
        lenexFile.setRequired(rules.lenexFileRequired());
        ((CardLayout) connectionCards.getLayout()).show(connectionCards, switch (fields.connectionType()) {
            case TCP -> TCP_CARD;
            case SERIAL -> SERIAL_CARD;
            case FILE -> FILE_CARD;
            case LENEX -> LENEX_CARD;
        });
        for (Component field : new Component[]{system, connectionType, host, port, serialPort, refreshPorts, serialMode, replayFile, chooseFile, lenexFile, chooseLenexFile, lenexInterval, competitionKey, timekeepingOffset, autoSave}) {
            field.setEnabled(rules.editable());
        }
        serverToggle.setEnabled(rules.serverEditable());
        restPort.setEnabled(rules.restPortEditable());
        serverToggle.setText(rules.serverText());
        recordToggle.setSelected(running);
        recordToggle.setText(rules.recordText());
        recordToggle.setEnabled(rules.recordEnabled());
        recordToggle.setToolTipText(rules.recordHint().orElse(null));
    }

    /** Offers exactly the given choices, keeping the selected one. */
    private <T> void offer(JComboBox<T> combo, List<T> choices) {
        List<T> offered = new ArrayList<>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            offered.add(combo.getItemAt(i));
        }
        if (offered.equals(choices)) {
            return;
        }
        boolean wasUpdating = updating;
        updating = true;
        try {
            Object selected = combo.getSelectedItem();
            DefaultComboBoxModel<T> model = new DefaultComboBoxModel<>();
            choices.forEach(model::addElement);
            combo.setModel(model);
            combo.setSelectedItem(selected);
        } finally {
            updating = wasUpdating;
        }
    }

    public void appendMessage(String text) {
        messageLog.append(text + "\n");
        int lines = messageLog.getLineCount();
        if (lines > MAX_LOG_LINES) {
            try {
                messageLog.replaceRange("", 0, messageLog.getLineStartOffset(lines - MAX_LOG_LINES));
            } catch (BadLocationException ex) {
                log.warn("Could not trim the message log", ex);
            }
        }
        messageLog.setCaretPosition(messageLog.getDocument().getLength());
    }

    JToggleButton recordToggle() {
        return recordToggle;
    }

    JTextField hostField() {
        return host;
    }

    JToggleButton serverToggle() {
        return serverToggle;
    }

    JSpinner serverPortSpinner() {
        return restPort;
    }

    List<Component> lockableInputs() {
        return List.of(system, connectionType, host, competitionKey, port, serialPort, refreshPorts, serialMode, replayFile, chooseFile, lenexFile, chooseLenexFile, lenexInterval, timekeepingOffset, autoSave, restPort, serverToggle);
    }

    JComboBox<Input> systemCombo() {
        return system;
    }

    JComboBox<ConnectionType> connectionTypeCombo() {
        return connectionType;
    }

    JTextField replayFileField() {
        return replayFile;
    }

    JComboBox<String> serialPortCombo() {
        return serialPort;
    }

    JTextArea logArea() {
        return messageLog;
    }

    JComboBox<SerialMode> serialModeCombo() {
        return serialMode;
    }

    JTextField competitionKeyField() {
        return competitionKey;
    }
}
