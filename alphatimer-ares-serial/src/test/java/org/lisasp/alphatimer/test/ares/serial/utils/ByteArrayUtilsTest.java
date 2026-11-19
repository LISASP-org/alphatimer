package org.lisasp.alphatimer.test.ares.serial.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.ares.serial.exceptions.InvalidDataException;
import org.lisasp.alphatimer.ares.serial.utils.ByteArrayUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteArrayUtilsTest {

    private ByteArrayUtils byteArrayUtils;

    @BeforeEach
    void initialize() {
        byteArrayUtils = new ByteArrayUtils();
    }

    @AfterEach
    void cleanup() {
        byteArrayUtils = null;
    }

    @Test
    void toByteValueInvalidTest() {
        assertThrows(InvalidDataException.class, () -> byteArrayUtils.toByteValue((byte) 0x2B));
    }

    @Test
    void toByteValueInvalid2Test() {
        assertThrows(InvalidDataException.class, () -> byteArrayUtils.toByteValue((byte) 0x2B, (byte) 0x30));
    }

    @Test
    void toByteValueInvalid3Test() {
        assertThrows(InvalidDataException.class, () -> byteArrayUtils.toByteValue((byte) 0x31, (byte) 0x40));
    }

    @Test
    void toShortValueInvalidTest() {
        assertThrows(InvalidDataException.class,
                     () -> byteArrayUtils.toShortValue((byte) 0x2B, (byte) 0x31, (byte) 0x32));
    }

    @Test
    void toByteValueWithOneSpaceTest() throws InvalidDataException {
        int result = byteArrayUtils.toByteValue((byte) 0x20);
        assertEquals(0, result);
    }

    @Test
    void toByteValueWithTwoSpacesTest() throws InvalidDataException {
        int result = byteArrayUtils.toByteValue((byte) 0x20, (byte) 0x20);
        assertEquals(0, result);
    }

    @Test
    void toShortValueWithThreeSpacesTest() throws InvalidDataException {
        int result = byteArrayUtils.toShortValue((byte) 0x20, (byte) 0x20, (byte) 0x20);
        assertEquals(0, result);
    }

    @Test
    void toByteValueWith2Test() throws InvalidDataException {
        int result = byteArrayUtils.toByteValue((byte) 0x32);
        assertEquals(2, result);
    }

    @Test
    void toByteValueWith23Test() throws InvalidDataException {
        int result = byteArrayUtils.toByteValue((byte) 0x32, (byte) 0x33);
        assertEquals(23, result);
    }

    @Test
    void toShortValueWith123Test() throws InvalidDataException {
        int result = byteArrayUtils.toShortValue((byte) 0x31, (byte) 0x32, (byte) 0x33);
        assertEquals(123, result);
    }

    @Test
    void toShortValueWith321Test() throws InvalidDataException {
        int result = byteArrayUtils.toShortValue((byte) 0x33, (byte) 0x32, (byte) 0x31);
        assertEquals(321, result);
    }

    @Test
    void extractEmptyTimeWithEmptyString() {
        // placeholder: -.--
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{1, 2, 8, 10, 51, 32, 49, 2, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 4},
                                                        8,
                                                        11);
        assertEquals(0, result);
    }

    @Test
    void extractEmptyTimeWithPlaceholderTest() {
        // placeholder: -.--
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{1, 2, 8, 10, 51, 32, 49, 2, 32, 32, 32, 32, 32, 32, 32, 45, 46, 45, 45, 32, 4},
                                                        8,
                                                        11);
        assertEquals(0, result);
    }

    @Test
    void extractEmptyTimeWithDNSTest() {
        // placeholder: -.--
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{1, 2, 8, 10, 51, 32, 49, 2, 32, 32, 32, 32, 32, 32, 32, 32, 0x44, 0x4E, 0x53, 32, 4},
                                                        8,
                                                        11);
        assertEquals(0, result);
    }

    @Test
    void extractEmptyTimeWithDSQTest() {
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{1, 2, 8, 10, 51, 32, 49, 2, 32, 32, 32, 32, 32, 32, 32, 32, 0x44, 0x53, 0x51, 32, 4},
                                                        8,
                                                        11);
        assertEquals(0, result);
    }

    @Test
    void extractEmptyTimeWithDNFTest() {
        // placeholder: -.--
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{1, 2, 8, 10, 51, 32, 49, 2, 32, 32, 32, 32, 32, 32, 32, 32, 0x44, 0x4E, 0x46, 32, 4},
                                                        8,
                                                        11);
        assertEquals(0, result);
    }

    @Test
    void extractEmptyTimeWithMarkedPlaceholderTest() {
        // placeholder: + -.--
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{1, 2, 8, 10, 51, 32, 49, 2, 32, 32, 32, 32, 32, 43, 32, 45, 46, 45, 45, 32, 4},
                                                        8,
                                                        11);
        assertEquals(0, result);
    }

    @Test
    void extractTimeWithHoursInMillis0600WithPlusTest() {
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 43, 0x30, 0x2E, 0x36, 0x30, 0x20},
                                                        0,
                                                        11);
        assertEquals(600, result);
    }

    @Test
    void extractTimeWithHoursInMillis0600WithMinusTest() {
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 45, 0x30, 0x2E, 0x36, 0x30, 0x20},
                                                        0,
                                                        11);
        assertEquals(600, result);
    }

    @Test
    void extractTimeWithHoursInMillis0600Test() {
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x30, 0x2E, 0x36, 0x30, 0x20},
                                                        0,
                                                        11);
        assertEquals(600, result);
    }

    @Test
    void extractTimeWithHoursInMillis112853930Test() {
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{0x33, 0x31, 0x3A, 0x32, 0x30, 0x3A, 0x35, 0x33, 0x2E, 0x39, 0x33, 0x20},
                                                        0,
                                                        11);
        assertEquals(112853930, result);
    }

    @Test
    void extractTimeWithHoursAndPrefixInMillis112853930Test() {
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{0x44, 0x33, 0x31, 0x3A, 0x32, 0x30, 0x3A, 0x35, 0x33, 0x2E, 0x39, 0x33, 0x20},
                                                        1,
                                                        12);
        assertEquals(112853930, result);
    }

    @Test
    void extractTimeWithHoursInMillis112853900Test() {
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{0x33, 0x31, 0x3A, 0x32, 0x30, 0x3A, 0x35, 0x33, 0x2E, 0x39, 0x30, 0x20},
                                                        0,
                                                        11);
        assertEquals(112853900, result);
    }

    @Test
    void extractTimeWithoutHoursInMillisTest() {
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{0x20, 0x20, 0x20, 0x32, 0x30, 0x3A, 0x35, 0x33, 0x2E, 0x39, 0x33, 0x20},
                                                        0,
                                                        11);
        assertEquals(1253930, result);
    }

    @Test
    void extractTimeWithoutHoursAndMinutesInMillisTest() {
        int result = byteArrayUtils.extractTimeInMillis(new byte[]{0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x35, 0x33, 0x2E, 0x39, 0x33, 0x20},
                                                        0,
                                                        11);
        assertEquals(53930, result);
    }

    @Test()
    void extractTimeInMillisInvalidTest1() {
        assertThrows(NumberFormatException.class,
                     () -> byteArrayUtils.extractTimeInMillis(new byte[]{0x44, 0x31, 0x3A, 0x32, 0x30, 0x3A, 0x35, 0x33, 0x2E, 0x39, 0x33, 0x20},
                                                              0,
                                                              11));
    }

    @Test()
    void extractTimeInMillisInvalidTest2() {
        assertThrows(NumberFormatException.class,
                     () -> byteArrayUtils.extractTimeInMillis(new byte[]{0x33, 0x44, 0x3A, 0x32, 0x30, 0x3A, 0x35, 0x33, 0x2E, 0x39, 0x33, 0x20},
                                                              0,
                                                              11));
    }

    @Test()
    void extractTimeInMillisInvalidTest3() {
        assertThrows(NumberFormatException.class,
                     () -> byteArrayUtils.extractTimeInMillis(new byte[]{0x33, 0x31, 0x44, 0x32, 0x30, 0x3A, 0x35, 0x33, 0x2E, 0x39, 0x33, 0x20},
                                                              0,
                                                              11));
    }

    @Test()
    void extractTimeInMillisInvalidTest4() {
        assertThrows(NumberFormatException.class,
                     () -> byteArrayUtils.extractTimeInMillis(new byte[]{0x33, 0x31, 0x3A, 0x44, 0x30, 0x3A, 0x35, 0x33, 0x2E, 0x39, 0x33, 0x20},
                                                              0,
                                                              11));
    }

    @Test()
    void extractTimeInMillisInvalidTest5() {
        assertThrows(NumberFormatException.class,
                     () -> byteArrayUtils.extractTimeInMillis(new byte[]{0x33, 0x31, 0x3A, 0x32, 0x44, 0x3A, 0x35, 0x33, 0x2E, 0x39, 0x33, 0x20},
                                                              0,
                                                              11));
    }

    @Test()
    void extractTimeInMillisInvalidTest6() {
        assertThrows(NumberFormatException.class,
                     () -> byteArrayUtils.extractTimeInMillis(new byte[]{0x33, 0x31, 0x3A, 0x32, 0x30, 0x44, 0x35, 0x33, 0x2E, 0x39, 0x33, 0x20},
                                                              0,
                                                              11));
    }

    @Test()
    void extractTimeInMillisInvalidTest7() {
        assertThrows(NumberFormatException.class,
                     () -> byteArrayUtils.extractTimeInMillis(new byte[]{0x33, 0x31, 0x3A, 0x32, 0x30, 0x3A, 0x44, 0x33, 0x2E, 0x39, 0x33, 0x20},
                                                              0,
                                                              11));
    }

    @Test()
    void extractTimeInMillisInvalidTest8() {
        assertThrows(NumberFormatException.class,
                     () -> byteArrayUtils.extractTimeInMillis(new byte[]{0x33, 0x31, 0x3A, 0x32, 0x30, 0x3A, 0x35, 0x44, 0x2E, 0x39, 0x33, 0x20},
                                                              0,
                                                              11));
    }

    @Test()
    void extractTimeInMillisInvalidTest9() {
        assertThrows(NumberFormatException.class,
                     () -> byteArrayUtils.extractTimeInMillis(new byte[]{0x33, 0x31, 0x3A, 0x32, 0x30, 0x3A, 0x35, 0x33, 0x44, 0x39, 0x33, 0x20},
                                                              0,
                                                              11));
    }

    @Test()
    void extractTimeInMillisInvalidTest10() {
        assertThrows(NumberFormatException.class,
                     () -> byteArrayUtils.extractTimeInMillis(new byte[]{0x33, 0x31, 0x3A, 0x32, 0x30, 0x3A, 0x35, 0x33, 0x2E, 0x44, 0x33, 0x20},
                                                              0,
                                                              11));
    }

    @Test()
    void extractTimeInMillisInvalidTest11() {
        assertThrows(NumberFormatException.class,
                     () -> byteArrayUtils.extractTimeInMillis(new byte[]{0x33, 0x31, 0x3A, 0x32, 0x30, 0x3A, 0x35, 0x33, 0x2E, 0x39, 0x44, 0x20},
                                                              0,
                                                              11));
    }

    @Test()
    void extractTimeInMillisInvalidFormatDotBeforeColon() {
        assertThrows(NumberFormatException.class,
                     () -> byteArrayUtils.extractTimeInMillis(new byte[]{0x44, 0x33, 0x31, 0x3A, 0x32, 0x30, 0x2E, 0x35, 0x33, 0x3A, 0x39, 0x33, 0x20},
                                                              1,
                                                              12));
    }
}
