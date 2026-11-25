package org.lisasp.alphatiming.lenex.api;

public final class XmlParserUnavailableException extends RuntimeException {

    public XmlParserUnavailableException(Throwable cause) {
        super("The JDK's XML parser lacks a required feature", cause);
    }
}
