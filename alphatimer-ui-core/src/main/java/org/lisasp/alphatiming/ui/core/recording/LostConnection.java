package org.lisasp.alphatiming.ui.core.recording;

import org.lisasp.alphatiming.ui.core.settings.ConnectionType;

/** {@code address} is the serial port, {@code host:port} or the replay file, depending on the type. */
public record LostConnection(ConnectionType type, String address) {
}
