package de.dennisfabri.alphatimer.serial;

import de.dennisfabri.alphatimer.serial.exceptions.NoPortsFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultSerialConnectionBuilderTest {

    @Test
    void listAvailablePorts() {
        if (!TestUtil.isTestWithSerialHardwareEnabled()) {
            return;
        }
        assertNotNull(new DefaultSerialConnectionBuilder().listAvailablePorts());
    }

    @Test
    void autoconfigurePort() throws NoPortsFoundException {
        if (!TestUtil.isTestWithSerialHardwareEnabled()) {
            return;
        }
        assertNotNull(new DefaultSerialConnectionBuilder().autoconfigurePort());
    }
}
