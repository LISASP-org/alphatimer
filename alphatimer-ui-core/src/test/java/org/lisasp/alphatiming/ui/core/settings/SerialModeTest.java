package org.lisasp.alphatiming.ui.core.settings;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.serial.configuration.DataBits;
import org.lisasp.alphatimer.api.serial.configuration.Parity;
import org.lisasp.alphatimer.api.serial.configuration.StopBits;

import static org.assertj.core.api.Assertions.assertThat;

class SerialModeTest {

    @Test
    void ares21Uses7E1At9600() {
        assertThat(SerialMode.ARES21.configuration().getBaud()).isEqualTo(9600);
        assertThat(SerialMode.ARES21.configuration().getDatabits()).isEqualTo(DataBits.Seven);
        assertThat(SerialMode.ARES21.configuration().getStopbits()).isEqualTo(StopBits.One);
        assertThat(SerialMode.ARES21.configuration().getParity()).isEqualTo(Parity.Even);
    }

    @Test
    void quantumUses8N1At9600() {
        assertThat(SerialMode.QUANTUM.configuration().getBaud()).isEqualTo(9600);
        assertThat(SerialMode.QUANTUM.configuration().getDatabits()).isEqualTo(DataBits.Eight);
        assertThat(SerialMode.QUANTUM.configuration().getStopbits()).isEqualTo(StopBits.One);
        assertThat(SerialMode.QUANTUM.configuration().getParity()).isEqualTo(Parity.None);
    }
}
