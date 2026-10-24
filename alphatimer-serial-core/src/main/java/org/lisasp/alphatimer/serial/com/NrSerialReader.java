package org.lisasp.alphatimer.serial.com;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.UnsupportedCommOperationException;
import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.alphatimer.api.serial.configuration.SerialConfiguration;
import org.lisasp.basics.notification.primitive.ByteConsumer;
import org.lisasp.basics.notification.primitive.ByteNotifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.TooManyListenersException;

@Slf4j
public final class NrSerialReader implements SerialPortReader {

    private static final int OPEN_TIMEOUT_MILLIS = 2000;

    private final String portName;
    private final SerialConfiguration configuration;
    private final ByteNotifier notifier = new ByteNotifier();

    private volatile SerialPort port;
    private volatile InputStream input;

    public NrSerialReader(String portName, SerialConfiguration configuration) {
        this.portName = portName;
        this.configuration = configuration;
    }

    @Override
    public SerialPortReader register(ByteConsumer listener) {
        notifier.register(listener);
        return this;
    }

    @Override
    public synchronized void start() {
        if (port != null) {
            return;
        }
        SerialPort opened = null;
        try {
            opened = (SerialPort) find().open("AlphaTiming", OPEN_TIMEOUT_MILLIS);
            opened.setSerialPortParams(configuration.getBaud(),
                                       dataBits(configuration),
                                       stopBits(configuration),
                                       parity(configuration));
            input = opened.getInputStream();
            opened.addEventListener(this::onEvent);
            opened.notifyOnDataAvailable(true);
            port = opened;
            log.info("Reading from serial port {} ({})", portName, configuration);
        } catch (PortInUseException ex) {
            closeQuietly(opened);
            throw new SerialConnectionException(portName, SerialConnectionException.Reason.IN_USE, ex);
        } catch (UnsupportedCommOperationException | TooManyListenersException | IOException ex) {
            closeQuietly(opened);
            throw new SerialConnectionException(portName, SerialConnectionException.Reason.OPEN_FAILED, ex);
        } catch (LinkageError ex) {
            closeQuietly(opened);
            throw new SerialConnectionException(portName, SerialConnectionException.Reason.UNAVAILABLE, ex);
        }
    }

    private static int dataBits(SerialConfiguration configuration) {
        return switch (configuration.getDatabits()) {
            case Five -> SerialPort.DATABITS_5;
            case Six -> SerialPort.DATABITS_6;
            case Seven -> SerialPort.DATABITS_7;
            case Eight -> SerialPort.DATABITS_8;
        };
    }

    private static int stopBits(SerialConfiguration configuration) {
        return switch (configuration.getStopbits()) {
            case One -> SerialPort.STOPBITS_1;
            case Two -> SerialPort.STOPBITS_2;
            case OneFive -> SerialPort.STOPBITS_1_5;
        };
    }

    private static int parity(SerialConfiguration configuration) {
        return switch (configuration.getParity()) {
            case None -> SerialPort.PARITY_NONE;
            case Odd -> SerialPort.PARITY_ODD;
            case Even -> SerialPort.PARITY_EVEN;
            case Mark -> SerialPort.PARITY_MARK;
            case Space -> SerialPort.PARITY_SPACE;
        };
    }

    private CommPortIdentifier find() {
        Enumeration<?> identifiers = CommPortIdentifier.getPortIdentifiers();
        while (identifiers.hasMoreElements()) {
            CommPortIdentifier id = (CommPortIdentifier) identifiers.nextElement();
            if (id.getPortType() == CommPortIdentifier.PORT_SERIAL && id.getName().equals(portName)) {
                return id;
            }
        }
        throw new SerialConnectionException(portName, SerialConnectionException.Reason.NOT_FOUND, null);
    }

    private void onEvent(SerialPortEvent event) {
        if (event.getEventType() != SerialPortEvent.DATA_AVAILABLE) {
            return;
        }
        InputStream in = input;
        if (in == null) {
            return;
        }
        byte[] buffer = new byte[1024];
        try {
            while (in.available() > 0) {
                int count = in.read(buffer);
                for (int x = 0; x < count; x++) {
                    notifier.accept(buffer[x]);
                }
            }
        } catch (IOException ex) {
            log.warn("Could not read from serial port {}", portName, ex);
        }
    }

    @Override
    public boolean isConnected() {
        return port != null;
    }

    @Override
    public synchronized void close() {
        SerialPort p = port;
        port = null;
        input = null;
        closeQuietly(p);
    }

    private static void closeQuietly(SerialPort p) {
        if (p == null) {
            return;
        }
        try {
            p.removeEventListener();
            p.close();
        } catch (RuntimeException ex) {
            log.warn("Could not close serial port", ex);
        }
    }
}
