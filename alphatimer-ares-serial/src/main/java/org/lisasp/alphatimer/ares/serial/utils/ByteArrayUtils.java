package org.lisasp.alphatimer.ares.serial.utils;

import lombok.AllArgsConstructor;
import org.lisasp.alphatimer.ares.serial.exceptions.InvalidDataException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@AllArgsConstructor
public class ByteArrayUtils {

    private String toString(byte... data) {
        return new String(data, StandardCharsets.UTF_8);
    }

    public char toCharacter(byte data) {
        return toString(data).charAt(0);
    }

    private static final byte SPACE = 0x20;
    private static final byte DOT = 0x2E;
    private static final byte COLON = 0x3A;
    private static final byte PLUS = 0x2B;
    private static final byte MINUS = 0x2D;

    private static final byte[] DIGITS = new byte[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, SPACE};
    private static final byte[] DIGITS_AND_DOTS = new byte[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, SPACE, DOT, COLON, PLUS, MINUS};

    private static final boolean[] allowedDigitsBitmap;
    private static final boolean[] allowedDigitsAndDotsBitmap;

    static {
        allowedDigitsBitmap = new boolean[256];
        for (byte d : DIGITS) {
            allowedDigitsBitmap[d] = true;
        }

        allowedDigitsAndDotsBitmap = new boolean[256];
        for (byte d : DIGITS_AND_DOTS) {
            allowedDigitsAndDotsBitmap[d] = true;
        }
    }


    private boolean hasOnlyDigits(byte... data) {
        for (byte candidate : data) {
            if (!allowedDigitsBitmap[candidate]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasOnlyDigitsAndDots(byte... data) {
        for (byte candidate : data) {
            if (!allowedDigitsAndDotsBitmap[candidate]) {
                return false;
            }
        }
        return true;
    }

    public byte toByteValue(byte digit) throws InvalidDataException {
        try {
            if (digit == 0x20) {
                // Field is unused and interpreted as zero
                return 0;
            }
            if (!hasOnlyDigits(digit)) {
                return throwCharacterNotAllowed();
            }
            return Byte.parseByte(toString(digit).trim());
        } catch (NumberFormatException nfe) {
            throw new InvalidDataException(
                    String.format("%d must be the ascii-representation of a digit.", digit),
                    nfe);
        }
    }

    private byte throwCharacterNotAllowed() {
        throw new NumberFormatException("Character not allowed");
    }

    public byte toByteValue(byte digit1, byte digit2) throws InvalidDataException {
        try {
            if (digit1 == 0x20 && digit2 == 0x20) {
                // Field is unused and interpreted as zero
                return 0;
            }
            if (!hasOnlyDigits(digit1, digit2)) {
                throwCharacterNotAllowed();
            }
            return Byte.parseByte(toString(digit1, digit2).trim());
        } catch (
                NumberFormatException nfe) {
            throw new InvalidDataException(
                    String.format(
                            "%d, %d must be the ascii-representation of a one or two digits number.",
                            digit1,
                            digit2),
                    nfe);
        }
    }

    public short toShortValue(byte digit1, byte digit2, byte digit3) throws InvalidDataException {
        try {
            if (digit1 == 0x20 && digit2 == 0x20 && digit3 == 0x20) {
                // Field is unused and interpreted as zero
                return 0;
            }
            if (!hasOnlyDigits(digit1, digit2, digit3)) {
                throwCharacterNotAllowed();
            }
            return Short.parseShort(toString(digit1, digit2, digit3).trim());
        } catch (
                NumberFormatException nfe) {
            throw new InvalidDataException(
                    String.format(
                            "%d, %d, %d must be the ascii-representation of a one to three digits number.",
                            digit1,
                            digit2,
                            digit3),
                    nfe);
        }
    }

    private byte[] extract(byte[] data, int offset, int length) {
        return Arrays.copyOfRange(data, offset, offset + length);
    }

    public String toString(byte[] data, int offset, int length) {
        return toString(extract(data, offset, length));
    }

    public int extractTimeInMillis(byte[] data, int offset, int length) {
        String text = toString(data, offset, length).trim();

        try {
            if (text.equals("")) {
                return 0;
            }
            if (text.equals("-.--")) {
                return 0;
            }
            if (text.equals("+ -.--")) {
                return 0;
            }
            if (text.equals("DNS")) {
                return 0;
            }
            if (text.equals("DNF")) {
                return 0;
            }
            if (text.equals("DSQ")) {
                return 0;
            }
            if (text.startsWith("+") || text.startsWith("-")) {
                text = text.substring(1);
            }
            if (text.indexOf('.') < text.indexOf(":")) {
                text = text.replaceFirst("\\.", ":");
            }
            if (!hasOnlyDigitsAndDots(extract(data, offset, length))) {
                throwCharacterNotAllowed();
            }

            int hours = 0;
            int minutes = 0;

            String[] parts = text.split(":");
            if (parts.length > 2) {
                hours = toInt(parts[parts.length - 3]);
            }
            if (parts.length > 1) {
                minutes = toInt(parts[parts.length - 2]);
            }

            return hours * 60 * 60 * 1000 + minutes * 60 * 1000 + toMillis(parts[parts.length - 1]);
        } catch (NumberFormatException nfe) {
            throw new NumberFormatException(String.format("'%s' is not a correct time representation", text));
        }
    }

    private int toMillis(String millis) {
        return (int) Math.round(toDouble(millis) * 1000);
    }

    private int toInt(String integerText) {
        return Integer.parseInt(integerText.trim());
    }

    private double toDouble(String doubleText) {
        return Double.parseDouble(doubleText.trim());
    }
}
