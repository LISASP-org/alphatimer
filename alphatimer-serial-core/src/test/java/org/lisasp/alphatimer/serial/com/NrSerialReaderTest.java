package org.lisasp.alphatimer.serial.com;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.serial.configuration.SerialConfiguration;

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
    void closeIsSafeWithoutStartAndRepeatable() {
        NrSerialReader reader = new NrSerialReader("COM1", SerialConfiguration.Quantum);
        reader.close();
        reader.close();
    }
}
