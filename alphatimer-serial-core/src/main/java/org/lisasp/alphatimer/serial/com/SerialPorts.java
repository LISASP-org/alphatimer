package org.lisasp.alphatimer.serial.com;

import gnu.io.CommPortIdentifier;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@Slf4j
public final class SerialPorts {

    /** nrjavaserial 5.2.1 aborts the JVM natively (fortify "buffer overflow detected") when it opens a name of 54 or more bytes. */
    public static final int MAX_NAME_BYTES = 48;

    private SerialPorts() {
    }

    public static boolean fitsDriver(String name) {
        return name.getBytes(StandardCharsets.UTF_8).length <= MAX_NAME_BYTES;
    }

    static List<String> offered(List<String> names) {
        List<String> ports = new ArrayList<>();
        for (String name : names) {
            if (fitsDriver(name)) {
                ports.add(name);
            } else {
                log.warn("Serial port {} is not offered: its name is longer than {} bytes", name, MAX_NAME_BYTES);
            }
        }
        ports.sort(String::compareToIgnoreCase);
        return ports;
    }

    /** The serial ports of this machine; empty (never an exception) when the native serial support is not available. */
    public static List<String> list() {
        List<String> ports = new ArrayList<>();
        try {
            Enumeration<?> identifiers = CommPortIdentifier.getPortIdentifiers();
            while (identifiers.hasMoreElements()) {
                CommPortIdentifier id = (CommPortIdentifier) identifiers.nextElement();
                if (id.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                    ports.add(id.getName());
                }
            }
        } catch (RuntimeException | LinkageError ex) {
            log.warn("Could not list serial ports", ex);
        }
        return offered(ports);
    }
}
