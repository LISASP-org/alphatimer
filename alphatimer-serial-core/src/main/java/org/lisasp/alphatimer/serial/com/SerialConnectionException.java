package org.lisasp.alphatimer.serial.com;

public class SerialConnectionException extends RuntimeException {

    public enum Reason { NOT_FOUND, IN_USE, UNAVAILABLE, OPEN_FAILED, NAME_TOO_LONG }

    private final String port;
    private final Reason reason;

    public SerialConnectionException(String port, Reason reason, Throwable cause) {
        super("Serial port " + port + ": " + reason, cause);
        this.port = port;
        this.reason = reason;
    }

    public String port() {
        return port;
    }

    public Reason reason() {
        return reason;
    }
}
