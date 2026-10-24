package org.lisasp.alphatiming.ui.core.recording;

final class RestServerStartException extends RuntimeException {

    private final int port;

    RestServerStartException(int port, Throwable cause) {
        super("REST port " + port, cause);
        this.port = port;
    }

    int port() {
        return port;
    }
}
