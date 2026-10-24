package org.lisasp.alphatiming.project.db;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Puts a finished snapshot of a project in place of its project file. The snapshot is consumed, also when writing fails;
 * a failure keeps the old project file, at worst as {@link #previousVersionOf(Path)}.
 */
public final class TimingFileWriter {

    @FunctionalInterface
    public interface FileMover {
        void moveAtomically(Path source, Path target) throws IOException;
    }

    private final FileMover mover;

    public TimingFileWriter() {
        this((source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE));
    }

    public TimingFileWriter(FileMover mover) {
        this.mover = mover;
    }

    public void write(Path snapshot, Path target) throws IOException {
        Path targetTmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.deleteIfExists(targetTmp);
            moveToTargetFolder(snapshot, targetTmp);
            if (Files.exists(target)) {
                Path previous = previousVersionOf(target);
                Files.deleteIfExists(previous);
                Files.move(target, previous, StandardCopyOption.ATOMIC_MOVE);
            }
            Files.move(targetTmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            FileSync.forceDirectory(target.toAbsolutePath().getParent());
        } catch (IOException | RuntimeException ex) {
            FileSync.deleteTempFile(snapshot);
            FileSync.deleteTempFile(targetTmp);
            throw ex instanceof IOException io ? io : new IOException("Could not save project file " + target, ex);
        }
    }

    private void moveToTargetFolder(Path snapshot, Path targetTmp) throws IOException {
        try {
            mover.moveAtomically(snapshot, targetTmp);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.copy(snapshot, targetTmp);
            FileSync.force(targetTmp);
            FileSync.deleteTempFile(snapshot);
        }
    }

    /** The file that keeps the state before the last save: {@code wk.timing} → {@code wk-previous.timing}. */
    public static Path previousVersionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String previous = dot > 0 ? name.substring(0, dot) + "-previous" + name.substring(dot) : name + "-previous";
        return file.resolveSibling(previous);
    }
}
