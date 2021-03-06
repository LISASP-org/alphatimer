package org.lisasp.alphatimer.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageAggregator;
import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageRepository;
import org.lisasp.alphatimer.api.ares.serial.events.messages.Ping;
import org.lisasp.alphatimer.api.serial.SerialPortReader;
import org.lisasp.alphatimer.api.serial.Storage;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageAggregator;
import org.lisasp.alphatimer.ares.serial.MessageConverter;
import org.lisasp.alphatimer.legacy.LegacySerialization;
import org.lisasp.alphatimer.legacy.LegacyService;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;
import org.lisasp.alphatimer.server.mq.Sender;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class SerialInterpreter {

    private final SerialPortReader reader;
    private final Storage storage;
    private final InputCollector inputCollector;

    private final DataHandlingMessageRepository messages;
    private final LegacyService legacy;
    private final MessageConverter messageConverter;
    private final DataHandlingMessageRefiner messageRefiner;
    private final Sender sender;

    @PostConstruct
    public void start() {
        initializePipeline();
        initializeSerialReader();
    }

    private void initializePipeline() {
        messageRefiner.register(m -> sender.send(m));

        DataHandlingMessageAggregator aggregator = new MessageAggregator();
        aggregator.register(legacy);
        aggregator.register(e -> messages.put(e));
        aggregator.register(messageRefiner);

        messageConverter.register(event -> {
            if (!(event instanceof Ping)) {
                log.info("Received message: '{}'", event);
            }
            aggregator.accept(event);
        });

        inputCollector.register(event -> {
            messageConverter.accept(event);
        });
    }

    private void initializeSerialReader() {
        reader.register(e -> {
            try {
                storage.write(e);
            } catch (IOException ex) {
                log.warn("Could not save data.", ex);
            }
        }).register(inputCollector);
    }


    public String getLegacyData() {
        return LegacySerialization.toXML(legacy.getHeats());
    }
}
