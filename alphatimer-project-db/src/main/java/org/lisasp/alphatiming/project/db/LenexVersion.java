package org.lisasp.alphatiming.project.db;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

public record LenexVersion(LocalDateTime arrival, byte[] xml) {

    @Override
    public boolean equals(Object o) {
        return o instanceof LenexVersion other && Objects.equals(arrival, other.arrival) && Arrays.equals(xml, other.xml);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(arrival) + Arrays.hashCode(xml);
    }

    @Override
    public String toString() {
        return "LenexVersion[arrival=" + arrival + ", " + xml.length + " bytes]";
    }
}
