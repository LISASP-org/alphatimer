package org.lisasp.alphatimer.api.serial;

import java.io.IOException;

public interface SerialPortWriter extends AutoCloseable {
    void write(byte b) throws IOException;

    @Override
    void close();
}
