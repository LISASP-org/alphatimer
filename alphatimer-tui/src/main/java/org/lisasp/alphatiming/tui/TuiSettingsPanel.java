package org.lisasp.alphatiming.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBox;
import com.googlecode.lanterna.gui2.ComboBox;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.FileDialogBuilder;
import org.lisasp.alphatimer.serial.com.SerialPorts;
import org.lisasp.alphatiming.ui.common.AppController;
import org.lisasp.alphatiming.ui.common.I18n;
import org.lisasp.alphatiming.ui.common.SettingsFormRules;
import org.lisasp.alphatiming.ui.common.TimeFormat;
import org.lisasp.alphatiming.ui.core.project.TimingDocument;
import org.lisasp.alphatiming.ui.core.settings.ConnectionType;
import org.lisasp.alphatiming.ui.core.settings.SerialMode;
import org.lisasp.alphatiming.ui.core.settings.Settings;

import java.io.File;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.regex.Pattern;

/** The "Settings & Recording" panel of the GUI: source, general settings, record button and the message log. */
final class TuiSettingsPanel extends Panel {

    private static final int MAX_LOG_LINES = 300;
    private static final Pattern DIGITS = Pattern.compile("\\d{0,5}");

    private final AppController controller;
    private final WindowBasedTextGUI gui;

    private final ComboBox<String> connectionType = new ComboBox<>(Arrays.stream(ConnectionType.values()).map(I18n::label).toList());
    private final CommitTextBox host = new CommitTextBox(30, this::commit);
    private final CommitTextBox port = new CommitTextBox(6, this::commit);
    private final ComboBox<String> serialPort = new ComboBox<>();
    private final Button refreshPorts = new Button(I18n.get("input.refresh"), this::loadPorts);
    private final ComboBox<String> serialMode = new ComboBox<>(Arrays.stream(SerialMode.values()).map(I18n::label).toList());
    private final CommitTextBox replayFile = new CommitTextBox(40, this::commit);
    private final Button chooseFile = new Button(I18n.get("input.chooseFile"), this::chooseReplayFile);
    private final CommitTextBox competitionKey = new CommitTextBox(30, this::commit);
    private final CommitTextBox autoSave = new CommitTextBox(6, this::commit);
    private final CommitTextBox restPort = new CommitTextBox(6, this::commit);
    private final CheckBox server = new CheckBox();
    private final Button record = new Button(I18n.get("recording.start"), this::toggleRecording);
    private final Label recordHint = new Label("");
    private final Label hostLabel = new Label(I18n.get("input.tcpHost"));
    private final Label replayFileLabel = new Label(I18n.get("input.replayFile"));
    private final TextBox log = new TextBox(new TerminalSize(60, 6), TextBox.Style.MULTI_LINE);
    private final Deque<String> logLines = new ArrayDeque<>();

    private final Panel tcpPanel = new Panel(new GridLayout(2));
    private final Panel serialPanel = new Panel(new GridLayout(2));
    private final Panel filePanel = new Panel(new GridLayout(2));

    private boolean updating;
    private boolean portsLoaded;

    TuiSettingsPanel(AppController controller, WindowBasedTextGUI gui) {
        super(new GridLayout(2));
        this.controller = controller;
        this.gui = gui;

        for (TextBox number : new TextBox[]{port, autoSave, restPort}) {
            number.setValidationPattern(DIGITS);
        }
        serialPort.setReadOnly(false);
        log.setReadOnly(true);

        addComponent(createSource().withBorder(Borders.singleLine(I18n.get("source.title"))),
                     GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true, false));
        addComponent(createGeneral().withBorder(Borders.singleLine(I18n.get("settings.title"))),
                     GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true, false));
        addComponent(log.withBorder(Borders.singleLine(I18n.get("messages.title"))),
                     GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true, true, 2, 1));

        connectionType.addListener((selected, previous, byUser) -> connectionChanged());
        serialPort.addListener((selected, previous, byUser) -> commit());
        serialMode.addListener((selected, previous, byUser) -> commit());
        server.addListener(checked -> commit());

        refresh();
    }

    private Panel createSource() {
        Panel type = new Panel(new GridLayout(2));
        type.addComponent(new Label(I18n.get("input.type")));
        type.addComponent(connectionType);

        tcpPanel.addComponent(hostLabel);
        tcpPanel.addComponent(host);
        tcpPanel.addComponent(new Label(I18n.get("input.tcpPort")));
        tcpPanel.addComponent(port);

        serialPanel.addComponent(new Label(I18n.get("input.serialPort")));
        serialPanel.addComponent(row(serialPort, refreshPorts));
        serialPanel.addComponent(new Label(I18n.get("input.protocol")));
        serialPanel.addComponent(serialMode);

        filePanel.addComponent(replayFileLabel);
        filePanel.addComponent(row(replayFile, chooseFile));

        Panel source = new Panel(new LinearLayout(Direction.VERTICAL));
        source.addComponent(type);
        source.addComponent(tcpPanel);
        source.addComponent(serialPanel);
        source.addComponent(filePanel);
        source.addComponent(row(record, recordHint));
        return source;
    }

    private Panel createGeneral() {
        Panel general = new Panel(new GridLayout(2));
        general.addComponent(new Label(I18n.get("general.competitionKey")));
        general.addComponent(competitionKey);
        general.addComponent(new Label(I18n.get("general.autoSave")));
        general.addComponent(autoSave);
        general.addComponent(new Label(I18n.get("general.serverPort")));
        general.addComponent(restPort);
        general.addComponent(new Label(""));
        general.addComponent(server);
        return general;
    }

    private static Panel row(com.googlecode.lanterna.gui2.Component... components) {
        Panel row = new Panel(new LinearLayout(Direction.HORIZONTAL));
        for (com.googlecode.lanterna.gui2.Component component : components) {
            row.addComponent(component);
        }
        return row;
    }

    private ConnectionType selectedType() {
        return ConnectionType.values()[Math.max(0, connectionType.getSelectedIndex())];
    }

    private Settings readFields() {
        return new Settings(selectedType(),
                            host.getText().trim(),
                            number(port),
                            serialPort.getText().trim(),
                            SerialMode.values()[Math.max(0, serialMode.getSelectedIndex())],
                            number(autoSave),
                            number(restPort),
                            server.isChecked(),
                            replayFile.getText().trim(),
                            competitionKey.getText().trim());
    }

    private static int number(TextBox box) {
        String text = box.getText().trim();
        return text.isEmpty() ? 0 : Integer.parseInt(text);
    }

    void commit() {
        if (updating) {
            return;
        }
        controller.applySettingsIfChanged(readFields());
        refreshButtons();
    }

    void toggleRecording() {
        if (controller.isRecording()) {
            controller.stopRecording();
        } else {
            commit();
            controller.startRecording();
        }
        refreshButtons();
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
        String current = serialPort.getText();
        boolean wasUpdating = updating;
        updating = true;
        try {
            serialPort.clearItems();
            SerialPorts.list().forEach(serialPort::addItem);
            selectPort(current);
            portsLoaded = true;
        } finally {
            updating = wasUpdating;
        }
    }

    private void selectPort(String name) {
        for (int i = 0; i < serialPort.getItemCount(); i++) {
            if (serialPort.getItem(i).equals(name)) {
                serialPort.setSelectedIndex(i);
                return;
            }
        }
        if (!name.isEmpty()) {
            serialPort.addItem(name);
            serialPort.setSelectedIndex(serialPort.getItemCount() - 1);
        }
    }

    private void chooseReplayFile() {
        File file = new FileDialogBuilder().setTitle(I18n.get("input.replayFile"))
                                           .setActionLabel(I18n.get("menu.open"))
                                           .build()
                                           .showDialog(gui);
        if (file != null) {
            replayFile.setText(file.getAbsolutePath());
            commit();
        }
    }

    /** Brings the fields in line with the document; fields being edited keep their text. */
    void refresh() {
        TimingDocument doc = controller.document();
        updating = true;
        try {
            if (doc != null) {
                Settings s = doc.settings();
                connectionType.setSelectedIndex(s.connectionType().ordinal());
                setUnlessFocused(competitionKey, s.competitionKey());
                setUnlessFocused(host, s.tcpHost());
                setUnlessFocused(port, String.valueOf(s.tcpPort()));
                setUnlessFocused(replayFile, s.replayFile());
                setUnlessFocused(autoSave, String.valueOf(s.autoSaveSeconds()));
                setUnlessFocused(restPort, String.valueOf(s.restPort()));
                server.setChecked(s.restEnabled());
                serialMode.setSelectedIndex(s.serialMode().ordinal());
                if (!serialPort.isFocused()) {
                    selectPort(s.serialPort());
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

    private static void setUnlessFocused(TextBox box, String text) {
        if (!box.isFocused() && !box.getText().equals(text)) {
            box.setText(text);
        }
    }

    private void refreshButtons() {
        Settings fields = readFields();
        SettingsFormRules rules = SettingsFormRules.of(fields, controller.isRecording(), controller.document() != null);
        tcpPanel.setVisible(fields.connectionType() == ConnectionType.TCP);
        serialPanel.setVisible(fields.connectionType() == ConnectionType.SERIAL);
        filePanel.setVisible(fields.connectionType() == ConnectionType.FILE);
        hostLabel.setText(I18n.get("input.tcpHost") + (rules.hostRequired() ? " *" : ""));
        replayFileLabel.setText(I18n.get("input.replayFile") + (rules.replayFileRequired() ? " *" : ""));
        connectionType.setEnabled(rules.editable());
        host.setEnabled(rules.editable());
        port.setEnabled(rules.editable());
        serialPort.setEnabled(rules.editable());
        refreshPorts.setEnabled(rules.editable());
        serialMode.setEnabled(rules.editable());
        replayFile.setEnabled(rules.editable());
        chooseFile.setEnabled(rules.editable());
        competitionKey.setEnabled(rules.editable());
        autoSave.setEnabled(rules.editable());
        server.setEnabled(rules.editable());
        restPort.setEnabled(rules.restPortEditable());
        server.setLabel(rules.serverText());
        record.setLabel(rules.recordText());
        record.setEnabled(rules.recordEnabled());
        recordHint.setText(rules.recordHint().orElse(""));
    }

    void appendMessage(String text) {
        logLines.addLast(TimeFormat.clock(LocalTime.now()) + "  " + text);
        while (logLines.size() > MAX_LOG_LINES) {
            logLines.removeFirst();
        }
        log.setText(String.join("\n", logLines));
        log.setCaretPosition(logLines.size() - 1, 0);
    }

    Button recordButton() {
        return record;
    }

    TextBox competitionKeyField() {
        return competitionKey;
    }

    TextBox hostField() {
        return host;
    }

    Panel tcpFields() {
        return tcpPanel;
    }

    Panel fileFields() {
        return filePanel;
    }

    ComboBox<String> connectionTypeCombo() {
        return connectionType;
    }
}
