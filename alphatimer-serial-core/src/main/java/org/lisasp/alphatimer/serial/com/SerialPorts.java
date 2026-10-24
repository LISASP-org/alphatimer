package org.lisasp.alphatimer.serial.com;

import gnu.io.CommPortIdentifier;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@Slf4j
public final class SerialPorts {

    private SerialPorts() {
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
        ports.sort(String::compareToIgnoreCase);
        return ports;
    }
}
