package org.lisasp.alphatiming.cli;

import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

/**
 * Fires on the first key pressed in the terminal and on Ctrl+C. Without a terminal (input redirected) a key cannot be read, and the
 * end of the input is not taken as a stop, so such a recording ends through a signal (Ctrl+C, SIGTERM).
 */
@Slf4j
final class TerminalStopTrigger implements StopTrigger {

    private Terminal terminal;
    private Attributes original;

    @Override
    public synchronized void start(Runnable onStop) {
        try {
            terminal = TerminalBuilder.builder().system(true).dumb(true).build();
            original = terminal.enterRawMode();
        } catch (IOException ex) {
            log.warn("No terminal for reading keys; the recording ends through a signal only", ex);
            return;
        }
        // JLine's default for a terminal ends the process on Ctrl+C without the JVM's shutdown hooks, so nothing would be saved.
        terminal.handle(Terminal.Signal.INT, signal -> onStop.run());
        Terminal t = terminal;
        Thread reader = new Thread(() -> {
            try {
                if (t.reader().read() >= 0) {
                    onStop.run();
                }
            } catch (IOException ex) {
                log.warn("Could not read from the terminal", ex);
            }
        }, "key-reader");
        reader.setDaemon(true);
        reader.start();
    }

    @Override
    public synchronized void close() {
        if (terminal == null) {
            return;
        }
        try {
            terminal.setAttributes(original);
            terminal.close();
        } catch (IOException ex) {
            log.warn("Could not restore the terminal", ex);
        }
        terminal = null;
    }
}
