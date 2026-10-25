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
import org.lisasp.alphatiming.ui.core.settings.SerialMode;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
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

    private final JComboBox<ConnectionType> connectionType = new JComboBox<>(ConnectionType.values());
    private final JWarningTextField replayFile = new JWarningTextField(true, false);
    private final JButton chooseFile = new JButton(I18n.get("input.chooseFile"));
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
        chooseFile.addActionListener(e -> chooseReplayFile());
        replayFile.addActionListener(e -> commit());
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
            refreshButtons();
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
        autoSave.addChangeListener(e -> commit());
        restPort.addChangeListener(e -> commit());
        serverToggle.addActionListener(e -> commit());

        refresh();
    }

    private static final String TCP_CARD = "tcp";
    private static final String SERIAL_CARD = "serial";
    private static final String FILE_CARD = "file";

    private JPanel createForm() {
        for (JSpinner spinner : new JSpinner[]{port, autoSave, restPort}) {
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
        competitionForm.add(I18n.get("general.autoSave"), autoSave);
        competitionForm.add(I18n.get("general.serverPort"), restPort);
        competitionForm.add((Component) null, serverToggle);
        JPanel competition = competitionForm.getPanel();
        competition.setBorder(BorderUtils.createLabeledBorder(I18n.get("settings.title"), true));

        FormLayout formLayout = new FormLayout("fill:default:grow, 4dlu, fill:default:grow", "fill:default");
        formLayout.setColumnGroups(new int[][]{{1, 3}});
        JPanel form = new JPanel(formLayout);
        form.add(input, CC.xy(1, 1));
        form.add(competition, CC.xy(3, 1));
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
        return new Settings(selectedType(), host.getText().trim(), (Integer) port.getValue(), selectedPort == null ? "" : selectedPort.toString().trim(), (SerialMode) serialMode.getSelectedItem(), (Integer) autoSave.getValue(), (Integer) restPort.getValue(), serverToggle.isSelected(), replayFile.getText().trim(), competitionKey.getText().trim());
    }

    private void chooseReplayFile() {
        String file = FileChooserUtils.openFile(SwingUtilities.getWindowAncestor(this));
        if (file != null) {
            replayFile.setText(file);
            commit();
        }
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
                connectionType.setSelectedItem(s.connectionType());
                if (replaced || !replayFile.hasFocus()) {
                    replayFile.setText(s.replayFile());
                }
                if (replaced || !competitionKey.hasFocus()) {
                    competitionKey.setText(s.competitionKey());
                }
                if (replaced || !host.hasFocus()) {
                    host.setText(s.tcpHost());
                }
                port.setValue(clamp(s.tcpPort(), 1, 65535));
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
        SettingsFormRules rules = SettingsFormRules.of(fields, running, controller.document() != null);
        host.setRequired(rules.hostRequired());
        replayFile.setRequired(rules.replayFileRequired());
        ((CardLayout) connectionCards.getLayout()).show(connectionCards, switch (fields.connectionType()) {
            case TCP -> TCP_CARD;
            case SERIAL -> SERIAL_CARD;
            case FILE -> FILE_CARD;
        });
        for (Component field : new Component[]{connectionType, host, port, serialPort, refreshPorts, serialMode, replayFile, chooseFile, competitionKey, autoSave, serverToggle}) {
            field.setEnabled(rules.editable());
        }
        restPort.setEnabled(rules.restPortEditable());
        serverToggle.setText(rules.serverText());
        recordToggle.setSelected(running);
        recordToggle.setText(rules.recordText());
        recordToggle.setEnabled(rules.recordEnabled());
        recordToggle.setToolTipText(rules.recordHint().orElse(null));
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
        return List.of(connectionType, host, competitionKey, port, serialPort, refreshPorts, serialMode, replayFile, chooseFile, autoSave, restPort, serverToggle);
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
