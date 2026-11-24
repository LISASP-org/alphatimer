package org.lisasp.alphatiming.ui.core.settings;

public enum ConnectionType {
    TCP, SERIAL, FILE, LENEX;

    public Input input() {
        return this == LENEX ? Input.LENEX : Input.ARES;
    }
}
