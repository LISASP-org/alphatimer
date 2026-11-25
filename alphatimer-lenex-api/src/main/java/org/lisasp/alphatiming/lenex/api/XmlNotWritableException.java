package org.lisasp.alphatiming.lenex.api;

public final class XmlNotWritableException extends RuntimeException {

    public XmlNotWritableException(Throwable cause) {
        super("The JDK cannot write the XML it has read", cause);
    }
}
