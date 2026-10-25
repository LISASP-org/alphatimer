package org.lisasp.alphatiming.project.db;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * An OS lock on a separate file next to the protected one. Only a held lock counts, so a lock file left behind by a crash
 * blocks nothing; the protected file itself stays unlocked because saving replaces it by renames.
 * <p>
 * Releasing deletes the lock file while the lock is still held. A program that opened the file just before and locks it
 * just after would hold a lock on a deleted file while another program locks the new file of the same name, so a new
 * holder writes a token into its file and checks that the path still shows it. The lock covers only the bytes after the
 * token: Windows locks are mandatory and would keep the token from being read through the path.
 */
@Slf4j
public final class LockFile implements AutoCloseable {

    private static final long LOCKED_FROM = 1024;
    private static final int STALE_ATTEMPTS = 3;

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

    interface Opener {
        FileChannel open(Path path) throws IOException;
    }

    /** Empty when a running program, this one included, already holds the lock. */
    public static Optional<LockFile> tryAcquire(Path path) throws IOException {
        return tryAcquire(path, p -> FileChannel.open(p, StandardOpenOption.CREATE, StandardOpenOption.WRITE));
    }

    static Optional<LockFile> tryAcquire(Path path, Opener opener) throws IOException {
        for (int attempt = 1; attempt <= STALE_ATTEMPTS; attempt++) {
            FileChannel channel = opener.open(path);
            FileLock lock;
            try {
                lock = channel.tryLock(LOCKED_FROM, Long.MAX_VALUE - LOCKED_FROM, false);
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
            if (isStillAt(path, channel)) {
                return Optional.of(new LockFile(path, channel, lock));
            }
            lock.release();
            channel.close();
        }
        return Optional.empty();
    }

    private static boolean isStillAt(Path path, FileChannel channel) throws IOException {
        byte[] token = UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII);
        channel.write(ByteBuffer.wrap(token), 0);
        try {
            return Arrays.equals(Files.readAllBytes(path), token);
        } catch (NoSuchFileException | AccessDeniedException ex) {
            return false;
        }
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
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.debug("Could not delete the lock file {}", path, ex);
        }
        close();
    }
}
