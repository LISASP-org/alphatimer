package org.lisasp.alphatiming.ui.common;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The problems the status bar shows until each is resolved; each kind is raised and cleared by its own rule. */
public final class StatusWarnings {

    /** Declared from the most to the least important; the status bar shows the most important one in front. */
    public enum Kind {
        CONNECTION_LOST,
        RECORDING_WRITE,
        AUTO_SAVE
    }

    public record Warning(String text, String details) {
    }

    private final Map<Kind, Warning> active = new EnumMap<>(Kind.class);

    public synchronized void raise(Kind kind, Warning warning) {
        active.put(kind, warning);
    }

    public synchronized void clear(Kind kind) {
        active.remove(kind);
    }

    public synchronized boolean isActive(Kind kind) {
        return active.containsKey(kind);
    }

    public synchronized List<Warning> active() {
        return List.copyOf(active.values());
    }
}
