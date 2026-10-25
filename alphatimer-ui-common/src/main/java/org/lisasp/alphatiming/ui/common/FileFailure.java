package org.lisasp.alphatiming.ui.common;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

/**
 * Why an action on a file failed, as far as the cause chain of its exception tells. It names the file the user acted on,
 * not the temp, lock or work file the exception may name, which the user does not know.
 */
public record FileFailure(Kind kind, Path file, String detail) {

    public enum Kind { NOT_FOUND, NO_PERMISSION, DISK_FULL, IS_DIRECTORY, CORRUPT, LOCKED, OTHER }

    private static final int SQLITE_PERM = 3;
    private static final int SQLITE_BUSY = 5;
    private static final int SQLITE_LOCKED = 6;
    private static final int SQLITE_READONLY = 8;
    private static final int SQLITE_CORRUPT = 11;
    private static final int SQLITE_FULL = 13;
    private static final int SQLITE_CANTOPEN = 14;
    private static final int SQLITE_NOTADB = 26;

    public static FileFailure of(Throwable ex, Path file) {
        Throwable root = ex;
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            Optional<Kind> kind = kindOf(t);
            if (kind.isPresent()) {
                return new FileFailure(kind.get(), file, detailOf(t));
            }
            root = t;
        }
        return new FileFailure(Kind.OTHER, file, detailOf(root));
    }

    private static Optional<Kind> kindOf(Throwable t) {
        return switch (t) {
            case NoSuchFileException _ -> Optional.of(Kind.NOT_FOUND);
            case AccessDeniedException _, ReadOnlyFileSystemException _ -> Optional.of(Kind.NO_PERMISSION);
            case SQLException sql -> kindOfSqlite(sql.getErrorCode());
            case FileSystemException fs -> kindOfReason(fs.getReason());
            case FileNotFoundException notFound -> kindOfReason(notFound.getMessage()).or(() -> Optional.of(Kind.NOT_FOUND));
            case IOException io -> kindOfReason(io.getMessage());
            default -> Optional.empty();
        };
    }

    /** sqlite-jdbc puts the primary result code into the vendor code, also for extended codes like READONLY_DBMOVED. */
    private static Optional<Kind> kindOfSqlite(int code) {
        return switch (code) {
            case SQLITE_PERM, SQLITE_READONLY, SQLITE_CANTOPEN -> Optional.of(Kind.NO_PERMISSION);
            case SQLITE_BUSY, SQLITE_LOCKED -> Optional.of(Kind.LOCKED);
            case SQLITE_CORRUPT, SQLITE_NOTADB -> Optional.of(Kind.CORRUPT);
            case SQLITE_FULL -> Optional.of(Kind.DISK_FULL);
            default -> Optional.empty();
        };
    }

    /** The JDK reports these only as the operating system's text (Linux, macOS, Windows). */
    private static Optional<Kind> kindOfReason(String reason) {
        String text = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (text.contains("no space left on device") || text.contains("not enough space on the disk")) {
            return Optional.of(Kind.DISK_FULL);
        }
        if (text.contains("is a directory")) {
            return Optional.of(Kind.IS_DIRECTORY);
        }
        if (text.contains("permission denied") || text.contains("access is denied") || text.contains("read-only file system")) {
            return Optional.of(Kind.NO_PERMISSION);
        }
        if (text.contains("being used by another process")) {
            return Optional.of(Kind.LOCKED);
        }
        return Optional.empty();
    }

    private static String detailOf(Throwable t) {
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }
}
