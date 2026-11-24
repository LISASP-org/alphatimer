package org.lisasp.alphatiming.lenex.api;

import java.io.IOException;

public class InvalidLenexException extends IOException {

    public InvalidLenexException(String message) {
        super(message);
    }

    public InvalidLenexException(String message, Throwable cause) {
        super(message, cause);
    }
}
