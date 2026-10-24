package org.lisasp.alphatimer.serial.com;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.serial.configuration.SerialConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NrSerialReaderTest {

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
    void aNameTooLongForTheDriverFailsOnStartBeforeTheDriverSeesIt() {
        String name = "/dev/" + "x".repeat(SerialPorts.MAX_NAME_BYTES - 4);
        NrSerialReader reader = new NrSerialReader(name, SerialConfiguration.ARES21);
        assertThatThrownBy(reader::start)
                .isInstanceOfSatisfying(SerialConnectionException.class, ex -> {
                    assertThat(ex.port()).isEqualTo(name);
                    assertThat(ex.reason()).isEqualTo(SerialConnectionException.Reason.NAME_TOO_LONG);
                });
        assertThat(reader.isConnected()).isFalse();
    }

    @Test
    void theDriverLimitCountsUtf8Bytes() {
        assertThat(SerialPorts.fitsDriver("x".repeat(SerialPorts.MAX_NAME_BYTES))).isTrue();
        assertThat(SerialPorts.fitsDriver("x".repeat(SerialPorts.MAX_NAME_BYTES + 1))).isFalse();
        assertThat(SerialPorts.fitsDriver("\u00E4".repeat(SerialPorts.MAX_NAME_BYTES / 2))).isTrue();
        assertThat(SerialPorts.fitsDriver("\u00E4".repeat(SerialPorts.MAX_NAME_BYTES / 2) + "x")).isFalse();
    }

    @Test
    void portsWithNamesTooLongForTheDriverAreNotOffered() {
        String fits = "/tmp/" + "y".repeat(SerialPorts.MAX_NAME_BYTES - 5);
        String tooLong = "/tmp/" + "z".repeat(SerialPorts.MAX_NAME_BYTES - 4);
        assertThat(SerialPorts.offered(List.of(tooLong, fits, "COM1"))).containsExactly(fits, "COM1");
    }

    @Test
    void closeIsSafeWithoutStartAndRepeatable() {
        NrSerialReader reader = new NrSerialReader("COM1", SerialConfiguration.Quantum);
        reader.close();
        reader.close();
    }
}
