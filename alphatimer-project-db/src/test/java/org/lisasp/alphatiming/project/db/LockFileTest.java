package org.lisasp.alphatiming.project.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LockFileTest {

    @TempDir
    Path dir;

    @Test
    void ownerGetsTheLockOnceAProbeReleasesIt() throws Exception {
        Path path = dir.resolve("work.lock");
        AtomicInteger pauses = new AtomicInteger();
        try (FileChannel probe = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock probing = probe.tryLock();

            Optional<LockFile> owned = LockFile.acquireOwn(path, () -> {
                pauses.incrementAndGet();
                probing.release();
            });

            assertThat(owned).isPresent();
            owned.get().close();
        }
        assertThat(pauses).hasValue(1);
    }

    @Test
    void aLockOnALockFileDeletedMeanwhileDoesNotCountWhileANewOneIsHeld() throws Exception {
        Path path = dir.resolve("wk.timing.lock");
        LockFile first = LockFile.tryAcquire(path).orElseThrow();
        FileChannel openedBeforeTheDeletion = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
        first.closeAndDelete();
        assertThat(path).doesNotExist();

        try (LockFile current = LockFile.tryAcquire(path).orElseThrow()) {
            Iterator<FileChannel> early = List.of(openedBeforeTheDeletion).iterator();
            Optional<LockFile> late = LockFile.tryAcquire(path, p -> early.hasNext() ? early.next()
                    : FileChannel.open(p, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE));

            assertThat(late).isEmpty();
            assertThat(LockFile.isHeld(path)).isTrue();
        } finally {
            openedBeforeTheDeletion.close();
        }
    }

    @Test
    void lockHeldByAnotherOwnerStaysInUse() throws Exception {
        Path path = dir.resolve("work.lock");
        AtomicInteger pauses = new AtomicInteger();
        try (LockFile other = LockFile.tryAcquire(path).orElseThrow()) {

            Optional<LockFile> owned = LockFile.acquireOwn(path, pauses::incrementAndGet);

            assertThat(owned).isEmpty();
        }
        assertThat(pauses).hasValue(LockFile.OWN_ATTEMPTS - 1);
    }
}
