package org.lisasp.alphatimer.serial.com;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SerialPortsTest {

    @TempDir
    Path dev;

    @Test
    void usbAdaptersWithoutAccessAreReportedSorted() throws IOException {
        create("ttyUSB1", "ttyACM0", "ttyUSB0");

        assertThat(SerialPorts.withoutAccess(dev, path -> false))
                .extracting(InaccessiblePort::name)
                .containsExactly(name("ttyACM0"), name("ttyUSB0"), name("ttyUSB1"));
    }

    @Test
    void accessibleAdaptersAreNotReported() throws IOException {
        create("ttyUSB0", "ttyUSB1");
        Set<Path> accessible = Set.of(dev.resolve("ttyUSB1"));

        assertThat(SerialPorts.withoutAccess(dev, accessible::contains))
                .extracting(InaccessiblePort::name)
                .containsExactly(name("ttyUSB0"));
    }

    @Test
    void onlyUsbAdaptersAreChecked() throws IOException {
        create("ttyS0", "tty1", "console", "ttyUSB", "ttyUSBx");

        assertThat(SerialPorts.withoutAccess(dev, path -> false)).isEmpty();
    }

    @Test
    void theGroupOfTheDeviceIsReported() throws IOException {
        create("ttyUSB0");
        Files.setPosixFilePermissions(dev.resolve("ttyUSB0"), PosixFilePermissions.fromString("rw-rw----"));
        String group = Files.readAttributes(dev.resolve("ttyUSB0"), java.nio.file.attribute.PosixFileAttributes.class).group().getName();

        assertThat(SerialPorts.withoutAccess(dev, path -> false))
                .extracting(InaccessiblePort::group)
                .containsExactly(group);
    }

    @Test
    void noGroupIsReportedWhenTheGroupMayNotReadAndWrite() throws IOException {
        create("ttyUSB0");
        Files.setPosixFilePermissions(dev.resolve("ttyUSB0"), PosixFilePermissions.fromString("rw-r-----"));

        assertThat(SerialPorts.withoutAccess(dev, path -> false))
                .extracting(InaccessiblePort::group)
                .containsExactly("");
    }

    @Test
    void missingDeviceDirectoryReportsNothing() {
        assertThat(SerialPorts.withoutAccess(dev.resolve("missing"), path -> false)).isEmpty();
    }

    @Test
    void checkingThisMachineNeverFails() {
        assertThat(SerialPorts.withoutAccess()).isNotNull();
    }

    private void create(String... names) throws IOException {
        for (String name : names) {
            Files.createFile(dev.resolve(name));
        }
    }

    private String name(String file) {
        return dev.resolve(file).toString();
    }
}
