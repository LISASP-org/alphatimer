package org.lisasp.alphatimer.server;

import org.junit.jupiter.api.Test;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.alphatimer.api.serial.Storage;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.legacy.LegacyService;
import org.lisasp.alphatimer.messagesstorage.AresMessage;
import org.lisasp.alphatimer.messagesstorage.AresMessageRepository;
import org.lisasp.alphatimer.messagesstorage.Messages;
import org.lisasp.alphatimer.ares.serial.MessageConverter;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;
import org.lisasp.basics.jre.date.DateTimeFacade;
import org.lisasp.basics.notification.primitive.ByteConsumer;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ContextConfiguration()
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RestServiceTest {

    @Autowired
    private LegacyJPARepository repository;

    @Test
    void getLegacyHeatsTest() {
        // SerialPortReader, Storage, InputCollector and AresMessageRepository are never exercised
        // by this test - it only calls restService.getLegacyHeats(), which only touches the
        // LegacyService collaborator. They exist purely to satisfy the constructor.
        SerialPortReader unusedReader = new SerialPortReader() {
            @Override
            public SerialPortReader register(ByteConsumer listener) {
                throw new UnsupportedOperationException("Not used by RestServiceTest");
            }

            @Override
            public void close() {
                throw new UnsupportedOperationException("Not used by RestServiceTest");
            }
        };
        Storage unusedStorage = new Storage("target/unused", new UnusedFileFacade(), new UnusedDateFacade());
        InputCollector unusedInputCollector = new InputCollector("TestWK", new UnusedDateTimeFacade());
        AresMessageRepository unusedAresMessageRepository = new AresMessageRepository() {
            @Override
            public List<AresMessage> findAllByCompetitionKeyAndEventAndHeat(String competitionKey, short event, byte heat) {
                throw new UnsupportedOperationException("Not used by RestServiceTest");
            }

            @Override
            public <S extends AresMessage> S save(S s) {
                throw new UnsupportedOperationException("Not used by RestServiceTest");
            }

            @Override
            public long count() {
                throw new UnsupportedOperationException("Not used by RestServiceTest");
            }
        };

        RestService restService = new RestService(
                new SerialInterpreter(
                        unusedReader,
                        unusedStorage,
                        unusedInputCollector,
                        new Messages(unusedAresMessageRepository),
                        new LegacyService(repository, "TestWK"),
                        new MessageConverter(),
                        new DataHandlingMessageRefiner()
                )
        );

        String actual = restService.getLegacyHeats();

        assertEquals("<AlphaServer.Heat-array/>", actual);
    }

    private static class UnusedFileFacade implements org.lisasp.basics.jre.io.FileFacade {
        @Override
        public void put(java.nio.file.Path file, byte... bytes) {
            throw new UnsupportedOperationException("Not used by RestServiceTest");
        }

        @Override
        public void append(java.nio.file.Path file, byte... bytes) {
            throw new UnsupportedOperationException("Not used by RestServiceTest");
        }

        @Override
        public byte[] get(java.nio.file.Path file) {
            throw new UnsupportedOperationException("Not used by RestServiceTest");
        }

        @Override
        public boolean exists(java.nio.file.Path path) {
            throw new UnsupportedOperationException("Not used by RestServiceTest");
        }

        @Override
        public void createDirectories(java.nio.file.Path path) {
            throw new UnsupportedOperationException("Not used by RestServiceTest");
        }

        @Override
        public java.util.stream.Stream<java.nio.file.Path> find(java.nio.file.Path basePath, int maxDepth, java.util.function.BiPredicate<java.nio.file.Path, java.nio.file.attribute.BasicFileAttributes> matcher) {
            throw new UnsupportedOperationException("Not used by RestServiceTest");
        }
    }

    private static class UnusedDateFacade implements org.lisasp.basics.jre.date.DateFacade {
        @Override
        public java.time.LocalDate today() {
            throw new UnsupportedOperationException("Not used by RestServiceTest");
        }
    }

    private static class UnusedDateTimeFacade implements DateTimeFacade {
        @Override
        public LocalDateTime now() {
            throw new UnsupportedOperationException("Not used by RestServiceTest");
        }
    }
}
