package org.lisasp.alphatiming.project.db;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;

/**
 * An OS lock on a separate file next to the protected one. Only a held lock counts, so a lock file left behind by a crash
 * blocks nothing; the protected file itself stays unlocked because saving replaces it by renames.
 */
@Slf4j
public final class LockFile implements AutoCloseable {

    private final Path path;
    private final FileChannel channel;
    private final FileLock lock;

    private LockFile(Path path, FileChannel channel, FileLock lock) {
        this.path = path;
        this.channel = channel;
        this.lock = lock;
    }

    public static Path besides(Path file) {
        return file.resolveSibling(file.getFileName() + ".lock");
    }

    /** Empty when a running program, this one included, already holds the lock. */
    public static Optional<LockFile> tryAcquire(Path path) throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException ex) {
            lock = null;
        } catch (IOException | RuntimeException ex) {
            channel.close();
            throw ex;
        }
        if (lock == null) {
            channel.close();
            return Optional.empty();
        }
        return Optional.of(new LockFile(path, channel, lock));
    }

    static final int OWN_ATTEMPTS = 5;
    private static final Duration OWN_PAUSE = Duration.ofMillis(50);

    interface Pause {
        void await() throws IOException;
    }

    /**
     * For a lock that only this program can rightfully take, such as the one of its own new work file: {@link #isHeld}
     * of another instance holds the lock for a moment, so a failed attempt is retried for about 200 ms before giving up.
     */
    public static Optional<LockFile> acquireOwn(Path path) throws IOException {
        return acquireOwn(path, LockFile::pause);
    }

    static Optional<LockFile> acquireOwn(Path path, Pause pause) throws IOException {
        for (int attempt = 1; ; attempt++) {
            Optional<LockFile> lock = tryAcquire(path);
            if (lock.isPresent() || attempt == OWN_ATTEMPTS) {
                return lock;
            }
            pause.await();
        }
    }

    private static void pause() throws InterruptedIOException {
        try {
            Thread.sleep(OWN_PAUSE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for a lock");
        }
    }

    /** Takes the lock for a moment to probe it, so a program acquiring it at that instant can miss it. */
    public static boolean isHeld(Path path) {
        if (!Files.exists(path)) {
            return false;
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            FileLock l = channel.tryLock();
            if (l == null) {
                return true;
            }
            l.release();
            return false;
        } catch (OverlappingFileLockException ex) {
            return true;
        } catch (IOException ex) {
            log.warn("Could not probe the lock {}; treating it as held", path, ex);
            return true;
        }
    }

    @Override
    public void close() {
        try {
            lock.release();
            channel.close();
        } catch (IOException ex) {
            log.warn("Could not release the lock {}", path, ex);
        }
    }

    public void closeAndDelete() {
        close();
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.debug("Could not delete the lock file {}", path, ex);
        }
    }
}
