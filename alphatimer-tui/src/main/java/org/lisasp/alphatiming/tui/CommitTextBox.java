package org.lisasp.alphatiming.tui;

import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

/** A single-line text box that hands its value on when it is left or Enter is pressed, like the GUI's text fields. */
final class CommitTextBox extends TextBox {

    private final Runnable onCommit;

    CommitTextBox(int columns, Runnable onCommit) {
        super(new com.googlecode.lanterna.TerminalSize(columns, 1));
        this.onCommit = onCommit;
    }

    @Override
    protected void afterLeaveFocus(FocusChangeDirection direction, Interactable nextInFocus) {
        super.afterLeaveFocus(direction, nextInFocus);
        onCommit.run();
    }

    @Override
    public synchronized Result handleKeyStroke(KeyStroke keyStroke) {
        if (keyStroke.getKeyType() == KeyType.Enter) {
            onCommit.run();
        }
        return super.handleKeyStroke(keyStroke);
    }
}
