package org.lisasp.alphatiming.cli;

/** Tells a running recording that the user wants it to end. */
public interface StopTrigger extends AutoCloseable {

    void start(Runnable onStop);

    @Override
    void close();
}
