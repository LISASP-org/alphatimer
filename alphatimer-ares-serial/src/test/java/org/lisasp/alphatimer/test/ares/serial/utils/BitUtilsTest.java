package org.lisasp.alphatimer.test.ares.serial.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lisasp.alphatimer.ares.serial.exceptions.InvalidDataException;
import org.lisasp.alphatimer.ares.serial.utils.BitUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class BitUtilsTest {

    private BitUtils bitUtils;

    @BeforeEach
    void initialize() {
        bitUtils = new BitUtils();
    }

    private final static byte[] SELECTED_LANE_DATA = new byte[]{0b00110000, 0b00101000, 0b00100100, 0b00100010, 0b00100001};

    @Test
    void extractUsedLanesNone() throws InvalidDataException {
        boolean[] expected = new boolean[]{false, false, false, false, false, false, false, false, false, false};

        boolean[] actual = bitUtils.extractUsedLanes((byte) 0b00100000, (byte) 0b00100000);

        assertArrayEquals(expected, actual);
    }

    @Test
    void extractUsedLanesC4() throws InvalidDataException {
        boolean[] expected = new boolean[]{false, true, true, false, false, false, false, true, false, false};

        boolean[] actual = bitUtils.extractUsedLanes((byte) (0b00100000 | 0x0C), (byte) (0b00100000 | 0x04));

        assertArrayEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(bytes = {0, 1, 2, 3, 4})
    void extractUsedLanesFromFirstByte(byte laneIndex) throws InvalidDataException {
        boolean[] expected = {false, false, false, false, false, false, false, false, false, false};
        expected[laneIndex] = true;

        boolean[] actual = bitUtils.extractUsedLanes(SELECTED_LANE_DATA[laneIndex], (byte) 0b00100000);

        assertArrayEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(bytes = {0, 1, 2, 3, 4})
    void extractUsedLanesFromSecondByte(byte laneIndex) throws InvalidDataException {
        boolean[] expected = {false, false, false, false, false, false, false, false, false, false};
        expected[5 + laneIndex] = true;

        boolean[] actual = bitUtils.extractUsedLanes((byte) 0b00100000, SELECTED_LANE_DATA[laneIndex]);

        assertArrayEquals(expected, actual);
    }
}
