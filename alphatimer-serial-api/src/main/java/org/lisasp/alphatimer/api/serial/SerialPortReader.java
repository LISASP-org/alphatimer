package org.lisasp.alphatimer.api.serial;

import org.lisasp.basics.notification.primitive.ByteConsumer;

public interface SerialPortReader extends AutoCloseable {
    SerialPortReader register(ByteConsumer listener);

    default void start() {
    }

    default boolean isConnected() {
        return true;
    }

    /**
     * Called once when a started reader loses its connection for good and has closed itself; never on the reader's own
     * reading thread, so a listener may stop the recording.
     */
    default void addConnectionLostListener(Runnable listener) {
    }

    @Override
    void close();
}
