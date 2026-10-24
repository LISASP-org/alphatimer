package org.lisasp.alphatimer.serial.tcp;

import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TcpReaderTest {

    @Test
    void doesNotConnectBeforeStartAndCloseIsSafe() {
        TcpReader reader = new TcpReader("localhost", 1);
        assertThat(reader.isConnected()).isFalse();
        reader.close();
        reader.close();
    }

    @Test
    void readsBytesAfterStartAndStopsAfterClose() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CopyOnWriteArrayList<Byte> received = new CopyOnWriteArrayList<>();
            TcpReader reader = new TcpReader("localhost", server.getLocalPort());
            reader.register(received::add);
            reader.start();
            try (Socket s = server.accept()) {
                OutputStream out = s.getOutputStream();
                out.write(new byte[]{1, 2, 3});
                out.flush();
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(received).containsExactly((byte) 1, (byte) 2, (byte) 3));
                assertThat(reader.isConnected()).isTrue();
            }
            reader.close();
            reader.close();
            assertThat(reader.isConnected()).isFalse();
        }
    }

    @Test
    void canBeRecreatedOnTheSamePort() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            for (int i = 0; i < 3; i++) {
                TcpReader reader = new TcpReader("localhost", server.getLocalPort());
                reader.start();
                server.accept().close();
                reader.close();
            }
        }
    }
}
