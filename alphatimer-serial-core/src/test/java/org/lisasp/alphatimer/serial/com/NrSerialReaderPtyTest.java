package org.lisasp.alphatimer.serial.com;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatimer.api.serial.configuration.SerialConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** A socat pty pair stands in for a USB serial adapter; killing socat makes the port vanish like unplugging it. */
class NrSerialReaderPtyTest {

    private static final Pattern PTY = Pattern.compile("PTY is (\\S+)");
    private static final String PORTS_PROPERTY = "gnu.io.rxtx.SerialPorts";

    private Process socat;
    private String device;
    private String peer;
    private String originalPorts;

    @TempDir
    Path dir;

    @BeforeEach
    void startPtyPair() throws IOException {
        assumeTrue(onPath("socat"), "socat is not installed");
        Path log = dir.resolve("socat.log");
        socat = new ProcessBuilder("socat", "-d", "-d", "pty,raw,echo=0", "pty,raw,echo=0").redirectError(log.toFile()).start();
        List<String> ptys = new ArrayList<>();
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            ptys.clear();
            Matcher m = PTY.matcher(Files.readString(log));
            while (m.find()) {
                ptys.add(m.group(1));
            }
            return ptys.size() == 2 || !socat.isAlive();
        });
        assumeTrue(ptys.size() == 2, "socat did not create a pty pair");
        device = ptys.get(0);
        peer = ptys.get(1);
        originalPorts = System.getProperty(PORTS_PROPERTY);
        System.setProperty(PORTS_PROPERTY, device);
    }

    @AfterEach
    void stopPtyPair() {
        if (socat != null) {
            socat.destroyForcibly();
        }
        if (originalPorts == null) {
            System.clearProperty(PORTS_PROPERTY);
        } else {
            System.setProperty(PORTS_PROPERTY, originalPorts);
        }
    }

    private static boolean onPath(String program) {
        String path = System.getenv("PATH");
        return path != null && Pattern.compile(File.pathSeparator, Pattern.LITERAL).splitAsStream(path)
                                       .anyMatch(dir -> Files.isExecutable(Path.of(dir, program)));
    }

    @Test
    void aVanishedPortClosesTheReaderAndIsReportedOnceAfterTheBytesReadBefore() throws Exception {
        NrSerialReader reader = new NrSerialReader(device, SerialConfiguration.Quantum);
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        List<String> lostOn = new CopyOnWriteArrayList<>();
        reader.register(b -> {
            synchronized (received) {
                received.write(b);
            }
        });
        reader.addConnectionLostListener(() -> lostOn.add(Thread.currentThread().getName()));
        try {
            reader.start();
            try (FileOutputStream out = new FileOutputStream(peer)) {
                out.write("hello".getBytes(StandardCharsets.US_ASCII));
            }
            await().atMost(10, TimeUnit.SECONDS).until(() -> {
                synchronized (received) {
                    return received.size() == 5;
                }
            });

            socat.destroy();

            await().atMost(10, TimeUnit.SECONDS).until(() -> !lostOn.isEmpty());
            assertThat(reader.isConnected()).isFalse();
            assertThat(lostOn).singleElement().satisfies(name -> assertThat(name).doesNotStartWith("RXTXPortMonitor"));
            synchronized (received) {
                assertThat(received.toString(StandardCharsets.US_ASCII)).isEqualTo("hello");
            }
        } finally {
            reader.close();
        }
    }
}
