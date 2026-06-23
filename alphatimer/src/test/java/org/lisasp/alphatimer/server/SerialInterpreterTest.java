package org.lisasp.alphatimer.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageRepository;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.alphatimer.api.serial.Storage;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageConverter;
import org.lisasp.alphatimer.legacy.LegacyService;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.*;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.basics.notification.primitive.ByteConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ContextConfiguration()
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SerialInterpreterTest {

    private static final List<DataHandlingMessage> EXPECTED_DATA_MESSAGE = List.of(new DataHandlingMessage(
            LocalDateTime.of(2021, 6, 21, 14, 53),
            "TestWK",
            new String(DataHandlingMessageTestData.message1),
            new String(DataHandlingMessageTestData.message2),
            MessageType.OnLineTime,
            KindOfTime.Start,
            TimeType.Empty,
            DataHandlingMessageTestData.createUsedLanes(),
            (byte) 2,
            (short) 1,
            (byte) 1,
            (byte) 0,
            RankInfo.Normal,
            (byte) 1,
            (byte) 0,
            112853930,
            TimeInfo.Normal,
            TimeMarker.Empty));

    @Autowired
    private LegacyJPARepository repository;

    private InputCollector inputCollector;
    private SerialInterpreter serialInterpreter;
    private TestDataHandlingMessageRepository messages;
    private ConfigurationValues config;

    private final static String expectedDate = "2021-04-17";

    @BeforeEach
    void prepare() {
        DateTimeFacade dateTimeFacade = new DateTimeFacade() {
            @Override
            public LocalDateTime now() {
                return LocalDateTime.of(2021, 6, 21, 14, 53);
            }
        };

        messages = new TestDataHandlingMessageRepository();

        MessageConverter messageConverter = new MessageConverter();

        inputCollector = new InputCollector("TestWK", dateTimeFacade);
        inputCollector.register(messageConverter);

        SerialPortReader reader = new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                return this;
            }

            @Override
            public void close() {

            }
        };

        // Storage and the second InputCollector argument are never exercised by SerialInterpreter
        // in these tests: `reader.register(...)` is a no-op stub that ignores its listener, so
        // neither storage.write(...) nor accept() on this InputCollector is ever reached.
        Storage unusedStorage = new Storage("target/unused", new UnusedFileFacade(), new UnusedDateFacade());
        InputCollector unusedInputCollector = new InputCollector("TestWK", new UnusedDateTimeFacade());

        serialInterpreter = new SerialInterpreter(reader,
                                                  unusedStorage,
                                                  unusedInputCollector,
                                                  messages,
                                                  new LegacyService(repository, config.getCompetitionKey()),
                                                  messageConverter,
                                                  new DataHandlingMessageRefiner()
        );
    }

    @Test
    void getLegacyData() {
        String result = serialInterpreter.getLegacyDataXML();
        assertEquals("<AlphaServer.Heat-array/>", result);
    }

    @Test
    void start() {
        serialInterpreter.start();

        assertTrue(messages.messages.isEmpty());
    }

    @Test
    void startWithAutoconfigure() {
        serialInterpreter.start();

        assertTrue(messages.messages.isEmpty());
    }

    @Test
    void startAndSend() {
        serialInterpreter.start();

        for (byte b : DataHandlingMessageTestData.message1) {
            inputCollector.accept(b);
        }
        for (byte b : DataHandlingMessageTestData.message2) {
            inputCollector.accept(b);
        }

        assertEquals(EXPECTED_DATA_MESSAGE, messages.messages);
    }

    @Test
    void onDestroy() {
        serialInterpreter.start();

        assertTrue(messages.messages.isEmpty());
    }

    private static class TestDataHandlingMessageRepository implements DataHandlingMessageRepository {

        private final List<DataHandlingMessage> messages = new ArrayList<>();

        @Override
        public void put(DataHandlingMessage message) {
            messages.add(message);
        }

        @Override
        public List<DataHandlingMessage> findBy(String competitionKey, short event, byte heat) {
            throw new UnsupportedOperationException("Not used by SerialInterpreterTest");
        }
    }

    private static class UnusedFileFacade implements org.lisasp.basics.jre.io.FileFacade {
        @Override
        public void put(java.nio.file.Path file, byte... bytes) {
            throw new UnsupportedOperationException("Not used by SerialInterpreterTest");
        }

        @Override
        public void append(java.nio.file.Path file, byte... bytes) {
            throw new UnsupportedOperationException("Not used by SerialInterpreterTest");
        }

        @Override
        public byte[] get(java.nio.file.Path file) {
            throw new UnsupportedOperationException("Not used by SerialInterpreterTest");
        }

        @Override
        public boolean exists(java.nio.file.Path path) {
            throw new UnsupportedOperationException("Not used by SerialInterpreterTest");
        }

        @Override
        public void createDirectories(java.nio.file.Path path) {
            throw new UnsupportedOperationException("Not used by SerialInterpreterTest");
        }

        @Override
        public java.util.stream.Stream<java.nio.file.Path> find(java.nio.file.Path basePath, int maxDepth, java.util.function.BiPredicate<java.nio.file.Path, java.nio.file.attribute.BasicFileAttributes> matcher) {
            throw new UnsupportedOperationException("Not used by SerialInterpreterTest");
        }
    }

    private static class UnusedDateFacade implements org.lisasp.basics.jre.date.DateFacade {
        @Override
        public java.time.LocalDate today() {
            throw new UnsupportedOperationException("Not used by SerialInterpreterTest");
        }
    }

    private static class UnusedDateTimeFacade implements DateTimeFacade {
        @Override
        public LocalDateTime now() {
            throw new UnsupportedOperationException("Not used by SerialInterpreterTest");
        }
    }
}
