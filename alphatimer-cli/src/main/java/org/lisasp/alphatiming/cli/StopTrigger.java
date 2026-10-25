package org.lisasp.alphatiming.cli;

public interface StopTrigger extends AutoCloseable {

    void start(Runnable onStop);

    @Override
    void close();
}
