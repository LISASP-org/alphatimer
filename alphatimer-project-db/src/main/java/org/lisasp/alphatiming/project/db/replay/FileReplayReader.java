package org.lisasp.alphatiming.project.db.replay;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.basics.notification.primitive.ByteConsumer;
import org.lisasp.basics.notification.primitive.ByteNotifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Replays recorded data as if it arrived on a serial line: a pure binary file is sent as is, an AlphaTiming project file
 * contributes its stored raw data. Bytes are paced like a serial line with 10 bits per byte (start, 8 data/parity
 * bits, stop), so 9600 baud are 960 bytes per second.
 */
@Slf4j
public final class FileReplayReader implements SerialPortReader {

    private static final int BITS_PER_BYTE = 10;
    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
    private static final long POLL_MILLIS = 5;

    private final Path file;
    private final double bytesPerSecond;
    private final ByteNotifier notifier = new ByteNotifier();
    private final List<Runnable> finishedListeners = new CopyOnWriteArrayList<>();

    private Thread thread;
    private volatile boolean running;

    public FileReplayReader(Path file, int baudRate) {
        this.file = file;
        this.bytesPerSecond = (double) baudRate / BITS_PER_BYTE;
    }

    @Override
    public SerialPortReader register(ByteConsumer listener) {
        notifier.register(listener);
        return this;
    }

    public void addFinishedListener(Runnable listener) {
        finishedListeners.add(listener);
    }

    @Override
    public synchronized void start() {
        if (thread != null) {
            return;
        }
        byte[] data = load();
        running = true;
        log.info("Replaying {} ({} bytes)", file.getFileName(), data.length);
        thread = new Thread(() -> replay(data), "file-replay-" + file.getFileName());
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public boolean isConnected() {
        return running;
    }

    @Override
    public synchronized void close() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void replay(byte[] data) {
        long begin = System.nanoTime();
        int sent = 0;
        try {
            while (running && sent < data.length) {
                long due = (long) ((System.nanoTime() - begin) / 1e9 * bytesPerSecond) + 1;
                int target = (int) Math.min(data.length, due);
                while (running && sent < target) {
                    deliver(data[sent++]);
                }
                if (sent < data.length) {
                    Thread.sleep(POLL_MILLIS);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        boolean completed = running && sent >= data.length;
        running = false;
        if (completed) {
            log.info("Replay of {} finished", file.getFileName());
            finishedListeners.forEach(Runnable::run);
        }
    }

    private void deliver(byte b) {
        try {
            notifier.accept(b);
        } catch (RuntimeException ex) {
            log.warn("Error while processing replayed data", ex);
        }
    }

    private byte[] load() {
        try {
            if (isProjectFile()) {
                return loadRawData();
            }
            return Files.readAllBytes(file);
        } catch (NoSuchFileException ex) {
            throw new ReplayFileException(file, ReplayFileException.Reason.NOT_FOUND, ex);
        } catch (IOException | SQLException ex) {
            throw new ReplayFileException(file, ReplayFileException.Reason.UNREADABLE, ex);
        }
    }

    private boolean isProjectFile() throws IOException {
        if (Files.isDirectory(file)) {
            throw new IOException("Is a directory: " + file);
        }
        byte[] header = new byte[SQLITE_HEADER.length];
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(header, 0, header.length) == header.length && Arrays.equals(header, SQLITE_HEADER);
        }
    }

    private byte[] loadRawData() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("open_mode", "1");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath(), properties);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select data from rawdata order by id")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while (rows.next()) {
                out.writeBytes(rows.getBytes(1));
            }
            return out.toByteArray();
        }
    }
}
