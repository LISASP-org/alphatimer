package org.lisasp.alphatiming.cli.analysis;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Recorded bytes as one printable line, control characters by name; long data is cut off. */
final class PrintableBytes {

    private static final int MAX_BYTES = 80;
    private static final Map<Integer, String> NAMES = Map.of(0x01, "SOH", 0x02, "STX", 0x04, "EOT", 0x08, "BS", 0x0A, "LF", 0x12, "DC2",
                                                             0x14, "DC4");

    private PrintableBytes() {
    }

    static String of(String text) {
        return of(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    static String of(byte[] data) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, MAX_BYTES); i++) {
            int b = data[i] & 0xFF;
            if (b >= 0x20 && b < 0x7F) {
                text.append((char) b);
            } else {
                text.append('<').append(NAMES.getOrDefault(b, "%02X".formatted(b))).append('>');
            }
        }
        if (data.length > MAX_BYTES) {
            text.append("...");
        }
        return text.toString();
    }
}
