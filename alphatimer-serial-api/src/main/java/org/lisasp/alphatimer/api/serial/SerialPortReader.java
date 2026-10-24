package org.lisasp.alphatimer.api.serial;

import org.lisasp.basics.notification.primitive.ByteConsumer;

public interface SerialPortReader extends AutoCloseable {
    SerialPortReader register(ByteConsumer listener);

    default void start() {
    }

    default boolean isConnected() {
        return true;
    }

    @Override
    void close();
}
