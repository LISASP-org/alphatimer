package org.lisasp.alphatimer.serial.com;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
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

    @Test
    void aLinkIsResolvedToTheDeviceItPointsTo() throws IOException {
        create("ttyUSB1");
        Path link = Files.createSymbolicLink(dev.resolve("usb-FTDI_FT232R_USB_UART_AB0N3MLA-if00-port0"), dev.resolve("ttyUSB1"));

        assertThat(SerialPorts.deviceOf(link.toString())).isEqualTo(dev.resolve("ttyUSB1").toRealPath().toString());
    }

    @Test
    void aNameThatIsNoLinkIsKept() {
        assertThat(SerialPorts.deviceOf("COM3")).isEqualTo("COM3");
        assertThat(SerialPorts.deviceOf(dev.resolve("ttyUSB9").toString())).isEqualTo(dev.resolve("ttyUSB9").toString());
    }

    @Test
    void aLongLinkToADeviceWithAShortNameFitsTheDriver() throws IOException {
        create("ttyUSB1");
        Path link = Files.createSymbolicLink(dev.resolve("x".repeat(SerialPorts.MAX_NAME_BYTES)), dev.resolve("ttyUSB1"));

        assertThat(SerialPorts.fitsDriver(link.toString())).isTrue();
    }

    @Test
    void anExistingDeviceWithALongNameDoesNotFitTheDriver() throws IOException {
        create("x".repeat(SerialPorts.MAX_NAME_BYTES));

        assertThat(SerialPorts.fitsDriver(dev.resolve("x".repeat(SerialPorts.MAX_NAME_BYTES)).toString())).isFalse();
    }

    @Test
    void aLongNameOfNoExistingDeviceFitsBecauseTheDriverNeverOpensIt() {
        assertThat(SerialPorts.fitsDriver(dev.resolve("usb-unplugged-adapter-with-a-long-stable-name-port0").toString())).isTrue();
    }

    @Test
    void theDriverLimitCountsUtf8Bytes() {
        assertThat(SerialPorts.fitsLength("x".repeat(SerialPorts.MAX_NAME_BYTES))).isTrue();
        assertThat(SerialPorts.fitsLength("x".repeat(SerialPorts.MAX_NAME_BYTES + 1))).isFalse();
        assertThat(SerialPorts.fitsLength("\u00E4".repeat(SerialPorts.MAX_NAME_BYTES / 2))).isTrue();
        assertThat(SerialPorts.fitsLength("\u00E4".repeat(SerialPorts.MAX_NAME_BYTES / 2) + "x")).isFalse();
    }

    @Test
    void portsWithNamesTooLongForTheDriverAreNotOffered() throws IOException {
        String fits = dev.resolve("y".repeat(SerialPorts.MAX_NAME_BYTES - dev.toString().length() - 1)).toString();
        String tooLong = dev.resolve("z".repeat(SerialPorts.MAX_NAME_BYTES)).toString();
        create(Path.of(fits).getFileName().toString(), Path.of(tooLong).getFileName().toString());

        assertThat(SerialPorts.offered(List.of(tooLong, fits, "COM1"))).containsExactly(fits, "COM1");
    }

    @Test
    void theStableNamesOfTheOfferedPortsAreOfferedToo() throws IOException {
        create("ttyUSB0", "ttyUSB1", "ttyUSB2");
        Path byId = Files.createDirectory(dev.resolve("by-id"));
        Path ftdi = Files.createSymbolicLink(byId.resolve("usb-FTDI_FT232R_USB_UART_AB0N3MLA-if00-port0"), Path.of("../ttyUSB1"));
        Path prolific = Files.createSymbolicLink(byId.resolve("usb-Prolific_Technology_Inc._USB-Serial_Controller-if00-port0"), dev.resolve("ttyUSB0"));
        Files.createSymbolicLink(byId.resolve("usb-not-offered-port0"), dev.resolve("ttyUSB2"));
        Files.createSymbolicLink(byId.resolve("usb-dangling-port0"), dev.resolve("ttyUSB7"));
        List<String> offered = List.of(dev.resolve("ttyUSB0").toString(), dev.resolve("ttyUSB1").toString());

        assertThat(SerialPorts.stableNames(byId, offered)).containsExactlyInAnyOrder(ftdi.toString(), prolific.toString());
    }

    @Test
    void withoutStableNamesThereAreNone() {
        assertThat(SerialPorts.stableNames(dev.resolve("by-id"), List.of("/dev/ttyUSB0"))).isEmpty();
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
