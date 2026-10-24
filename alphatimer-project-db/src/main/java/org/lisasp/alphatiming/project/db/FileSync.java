package org.lisasp.alphatiming.project.db;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Slf4j
final class FileSync {

    private FileSync() {
    }

    static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ex) {
            // Windows cannot open a directory as a channel; there the renames are not synced separately.
            log.debug("Could not sync directory {}", directory, ex);
        }
    }

    static void deleteTempFile(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ex) {
            log.warn("Could not delete temporary file {}", tmp, ex);
        }
    }
}
