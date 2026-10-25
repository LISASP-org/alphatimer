package org.lisasp.alphatimer.serial.com;

/** {@code group} is the device's group if its members may open it, otherwise empty. */
public record InaccessiblePort(String name, String group) {
}
