package org.lisasp.alphatiming.legacy.api;

import java.io.IOException;

public class InvalidLegacyXmlException extends IOException {

    public InvalidLegacyXmlException(String message) {
        super(message);
    }

    public InvalidLegacyXmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
