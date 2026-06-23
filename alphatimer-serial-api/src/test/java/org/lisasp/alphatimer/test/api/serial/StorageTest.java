package org.lisasp.alphatimer.test.api.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.serial.Storage;
import org.lisasp.basics.jre.date.DateFacade;
import org.lisasp.basics.jre.io.FileFacade;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class StorageTest {

    private static final LocalDate TIMESTAMP = LocalDate.of(2021, 4, 17);

    private Storage storage;
    private DateFacade dateTimeFacade;
    private TestFileFacade fileFacade;

    private static final String baseDir = "target/test-storage";

    private static final Path serialFile = Path.of("target", "test-storage", "2021-04-17.serial");

    @BeforeEach
    void prepare() throws IOException {
        dateTimeFacade = new DateFacade() {
            @Override
            public LocalDate today() {
                return TIMESTAMP;
            }
        };
        fileFacade = new TestFileFacade();
        storage = new Storage(baseDir, fileFacade, dateTimeFacade);
    }

    @AfterEach
    void cleanup() {
        storage = null;
        dateTimeFacade = null;
        fileFacade = null;
    }

    @Test
    void read() throws IOException {
        byte[] data = storage.read();

        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, data);
    }

    @Test
    void write() throws IOException {
        storage.write((byte) 0x01);
        storage.write((byte) 0x02);
        storage.write((byte) 0x03);
        storage.write((byte) 0x04);

        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, fileFacade.appended());
    }

    private static class TestFileFacade implements FileFacade {

        private final byte[] appended = new byte[1000];
        private int length = 0;

        byte[] appended() {
            return java.util.Arrays.copyOf(appended, length);
        }

        @Override
        public void put(Path file, byte... bytes) throws IOException {
            throw new IOException("Not implemented");
        }

        @Override
        public void append(Path file, byte... bytes) throws IOException {
            if (!file.equals(serialFile)) {
                throw new IOException("Wrong file");
            }
            System.arraycopy(bytes, 0, appended, length, bytes.length);
            length += bytes.length;
        }

        @Override
        public byte[] get(Path file) throws IOException {
            if (file.equals(serialFile)) {
                return new byte[]{0x01, 0x02, 0x03, 0x04};
            } else {
                return new byte[]{0x05, 0x06, 0x07, 0x08, 0x09};
            }
        }

        @Override
        public boolean exists(Path path) {
            return false;
        }

        @Override
        public void createDirectories(Path path) throws IOException {
            throw new IOException("Not implemented");
        }

        @Override
        public Stream<Path> find(Path basePath, int maxDepth, BiPredicate<Path, BasicFileAttributes> matcher) throws IOException {
            return Stream.empty();
        }
    }
}
