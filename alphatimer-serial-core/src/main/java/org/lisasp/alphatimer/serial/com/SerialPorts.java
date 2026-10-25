package org.lisasp.alphatimer.serial.com;

import gnu.io.CommPortIdentifier;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
public final class SerialPorts {

    /** nrjavaserial 5.2.1 aborts the JVM natively (fortify "buffer overflow detected") when it opens a name of 54 or more bytes. */
    public static final int MAX_NAME_BYTES = 48;

    private static final Path DEVICE_DIRECTORY = Path.of("/dev");
    private static final Path STABLE_NAMES = Path.of("/dev/serial/by-id");
    private static final Pattern USB_ADAPTER = Pattern.compile("tty(USB|ACM)\\d+");

    private SerialPorts() {
    }

    /**
     * Whether nrjavaserial may get the device of this name. A name of no existing device fits: nrjavaserial only opens
     * devices its own scan found, so its start fails as not found.
     */
    public static boolean fitsDriver(String name) {
        String device = deviceOf(name);
        return fitsLength(device) || !exists(device);
    }

    static boolean fitsLength(String name) {
        return name.getBytes(StandardCharsets.UTF_8).length <= MAX_NAME_BYTES;
    }

    /** The device a link such as {@code /dev/serial/by-id/…} points to; nrjavaserial only knows the device names. */
    public static String deviceOf(String name) {
        try {
            Path path = Path.of(name);
            return Files.isSymbolicLink(path) ? path.toRealPath().toString() : name;
        } catch (IOException | RuntimeException ex) {
            return name;
        }
    }

    private static boolean exists(String name) {
        try {
            return Files.exists(Path.of(name));
        } catch (RuntimeException ex) {
            return false;
        }
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
        ports.addAll(stableNames(STABLE_NAMES, ports));
        return offered(ports);
    }

    /** The links in {@code byIdDirectory} that point to one of the ports: names that stay the same when replugged. */
    static List<String> stableNames(Path byIdDirectory, List<String> ports) {
        if (!Files.isDirectory(byIdDirectory)) {
            return List.of();
        }
        try (Stream<Path> links = Files.list(byIdDirectory)) {
            return links.map(Path::toString)
                        .filter(link -> ports.contains(deviceOf(link)))
                        .toList();
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not list the stable serial port names in {}", byIdDirectory, ex);
            return List.of();
        }
    }

    /**
     * USB serial adapters that {@link #list()} cannot offer because this user may not open them. Only Linux restricts
     * them to a group: on Windows nrjavaserial lists the ports without opening them, on macOS every user may open them.
     */
    public static List<InaccessiblePort> withoutAccess() {
        if (!"Linux".equals(System.getProperty("os.name"))) {
            return List.of();
        }
        return withoutAccess(DEVICE_DIRECTORY, path -> Files.isReadable(path) && Files.isWritable(path));
    }

    static List<InaccessiblePort> withoutAccess(Path deviceDirectory, Predicate<Path> accessible) {
        if (!Files.isDirectory(deviceDirectory)) {
            return List.of();
        }
        try (Stream<Path> devices = Files.list(deviceDirectory)) {
            List<InaccessiblePort> ports = devices
                    .filter(device -> USB_ADAPTER.matcher(device.getFileName().toString()).matches())
                    .filter(accessible.negate())
                    .map(device -> new InaccessiblePort(device.toString(), group(device)))
                    .sorted(Comparator.comparing(InaccessiblePort::name, String::compareToIgnoreCase))
                    .toList();
            ports.forEach(port -> log.warn("Serial port {} is not offered: no access (group {})", port.name(), port.group()));
            return ports;
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not check the access to the serial devices in {}", deviceDirectory, ex);
            return List.of();
        }
    }

    private static String group(Path device) {
        try {
            PosixFileAttributes attributes = Files.readAttributes(device, PosixFileAttributes.class);
            boolean groupMayOpen = attributes.permissions().containsAll(Set.of(PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE));
            return groupMayOpen ? attributes.group().getName() : "";
        } catch (IOException | UnsupportedOperationException ex) {
            return "";
        }
    }
}
