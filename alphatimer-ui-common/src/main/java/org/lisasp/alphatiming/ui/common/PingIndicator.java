package org.lisasp.alphatiming.ui.common;

/**
 * What the ping marker of a front end shows: not connected, connected without a recent sign of life, a recent sign of
 * life, or a message that just arrived.
 */
public enum PingIndicator {
    DISCONNECTED, SILENT, BRIGHT, MESSAGE
}
