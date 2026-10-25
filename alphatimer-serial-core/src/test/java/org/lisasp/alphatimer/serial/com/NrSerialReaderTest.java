package org.lisasp.alphatimer.serial.com;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatimer.api.serial.configuration.SerialConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NrSerialReaderTest {

    @TempDir
    Path dir;

    @Test
    void listingPortsNeverFails() {
        assertThat(SerialPorts.list()).isNotNull();
    }

    @Test
    void unknownPortFailsOnStartWithTheNameOfThePort() {
        NrSerialReader reader = new NrSerialReader("/dev/does-not-exist-42", SerialConfiguration.ARES21);
        assertThat(reader.isConnected()).isFalse();
        assertThatThrownBy(reader::start)
                .isInstanceOfSatisfying(SerialConnectionException.class, ex -> {
                    assertThat(ex.port()).isEqualTo("/dev/does-not-exist-42");
                    assertThat(ex.reason()).isEqualTo(SerialConnectionException.Reason.NOT_FOUND);
                });
        assertThat(reader.isConnected()).isFalse();
    }

    @Test
    void aNameTooLongForTheDriverFailsOnStartBeforeTheDriverSeesIt() throws IOException {
        String name = Files.createFile(dir.resolve("x".repeat(SerialPorts.MAX_NAME_BYTES))).toString();
        NrSerialReader reader = new NrSerialReader(name, SerialConfiguration.ARES21);
        assertThatThrownBy(reader::start)
                .isInstanceOfSatisfying(SerialConnectionException.class, ex -> {
                    assertThat(ex.port()).isEqualTo(name);
                    assertThat(ex.reason()).isEqualTo(SerialConnectionException.Reason.NAME_TOO_LONG);
                });
        assertThat(reader.isConnected()).isFalse();
    }

    @Test
    void aStableNameIsOpenedAsTheDeviceItPointsToAndReportedUnderItsOwnName() throws IOException {
        Path device = Files.createFile(dir.resolve("ttyUSB1"));
        String name = Files.createSymbolicLink(dir.resolve("usb-FTDI_FT232R_USB_UART_AB0N3MLA-if00-port0"), device).toString();
        NrSerialReader reader = new NrSerialReader(name, SerialConfiguration.ARES21);
        assertThatThrownBy(reader::start)
                .isInstanceOfSatisfying(SerialConnectionException.class, ex -> {
                    assertThat(ex.port()).isEqualTo(name);
                    assertThat(ex.reason()).isEqualTo(SerialConnectionException.Reason.NOT_FOUND);
                });
    }

    @Test
    void aStableNameOfAnUnpluggedAdapterIsNotFound() {
        String name = dir.resolve("usb-Prolific_Technology_Inc._USB-Serial_Controller-if00-port0").toString();
        NrSerialReader reader = new NrSerialReader(name, SerialConfiguration.ARES21);
        assertThatThrownBy(reader::start)
                .isInstanceOfSatisfying(SerialConnectionException.class, ex -> assertThat(ex.reason()).isEqualTo(SerialConnectionException.Reason.NOT_FOUND));
    }

    @Test
    void closeIsSafeWithoutStartAndRepeatable() {
        NrSerialReader reader = new NrSerialReader("COM1", SerialConfiguration.Quantum);
        reader.close();
        reader.close();
    }
}
