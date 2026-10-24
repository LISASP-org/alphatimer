package org.lisasp.alphatimer.serial.tcp;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.basics.notification.primitive.ByteConsumer;
import org.lisasp.basics.notification.primitive.ByteNotifier;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

@Slf4j
public class TcpReader implements SerialPortReader {

    private static final int TIMEOUT_60s = 60000;

    private final ByteNotifier notifier = new ByteNotifier();

    private final String server;
    private final int port;

    private volatile Socket socket;
    private volatile InputStream input;

    private Thread reader;

    public TcpReader(String server, int port) {
        this.server = server;
        this.port = port;
    }

    @Override
    public synchronized void start() {
        if (reader != null) {
            return;
        }
        log.info("Starting reader at tcp://{}:{}", server, port);
        reader = new Thread(this::read, "tcp-reader-" + server + ":" + port);
        reader.setDaemon(true);
        reader.start();
    }

    @Override
    public boolean isConnected() {
        Socket s = socket;
        return s != null && s.isConnected() && !s.isClosed();
    }

    private void read() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                checkConnection();
                manageConnection();
                readOnce();
            } catch (SocketException ex) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                log.warn("Socket problem", ex);
                closeConnection();
                if (!sleep(30)) {
                    return;
                }
            } catch (IOException ex) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                log.warn("Exception while reading serial tcp input", ex);
                closeConnection();
                if (!sleep(1)) {
                    return;
                }
            }
        }
    }

    private void closeConnection() {
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void checkConnection() {
        Socket s = socket;
        if (s == null) {
            InputStream in = input;
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            input = null;
            return;
        }
        if (s.isClosed()) {
            socket = null;
            input = null;
        }
    }

    private boolean sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void manageConnection() {
        try {
            if (socket == null) {
                Socket s = new Socket();
                s.setSoTimeout(TIMEOUT_60s);
                socket = s;
                s.connect(new InetSocketAddress(server, port), TIMEOUT_60s);
                input = s.getInputStream();
            }
        } catch (IOException ex) {
            if (!Thread.currentThread().isInterrupted()) {
                log.warn("Could not connect to serial tcp server", ex);
            }
            closeConnection();
        }
    }

    private void readOnce() throws IOException {
        InputStream in = input;
        if (in == null) {
            sleepQuietly();
            return;
        }
        byte[] data = new byte[1024];
        int amount = in.read(data);
        if (amount < 0) {
            closeConnection();
            return;
        }
        for (int x = 0; x < amount; x++) {
            notifier.accept(data[x]);
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public SerialPortReader register(ByteConsumer listener) {
        notifier.register(listener);
        return this;
    }

    @Override
    public synchronized void close() {
        if (reader != null) {
            reader.interrupt();
        }
        InputStream in = input;
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                log.warn("Exception on socket input close.", e);
            }
        }
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                log.warn("Exception on socket close.", e);
            }
        }
        input = null;
        socket = null;
    }
}
