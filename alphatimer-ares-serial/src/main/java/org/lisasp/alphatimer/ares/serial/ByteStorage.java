package org.lisasp.alphatimer.ares.serial;

import org.lisasp.alphatimer.api.ares.serial.Characters;

import java.util.Arrays;

class ByteStorage {

    private static final int MaximumSize = 1024;

    private final byte[] data = new byte[MaximumSize];

    private int size = 0;

    void append(byte entry) {
        data[size] = entry;
        size++;
    }

    byte[] toByteArray() {
        return Arrays.copyOf(data, size);
    }

    byte lastEntry() {
        return data[size - 1];
    }

    private void clear() {
        size = 0;
    }

    byte[] extractAllButLatestEntry() {
        byte lastEntry = lastEntry();
        byte[] result = Arrays.copyOf(data, size - 1);
        clear();
        append(lastEntry);
        return result;
    }

    byte[] extractByteArray() {
        byte[] result = toByteArray();
        clear();
        return result;
    }

    public boolean isFull() {
        return size == data.length;
    }

    boolean endsWithEndOfMessage() {
        return lastEntry() == Characters.EOT_EndOfText;
    }

    public boolean endsWithStartOfMessage() {
        return lastEntry() == Characters.SOH_StartOfHeader;
    }
}
