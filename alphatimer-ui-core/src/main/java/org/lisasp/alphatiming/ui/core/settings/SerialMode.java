package org.lisasp.alphatiming.ui.core.settings;

import org.lisasp.alphatimer.api.serial.configuration.SerialConfiguration;

/** Line settings of the two supported protocols, as in AlphaServer. */
public enum SerialMode {
    ARES21(SerialConfiguration.ARES21),
    QUANTUM(SerialConfiguration.Quantum);

    private final SerialConfiguration configuration;

    SerialMode(SerialConfiguration configuration) {
        this.configuration = configuration;
    }

    public SerialConfiguration configuration() {
        return configuration;
    }
}
