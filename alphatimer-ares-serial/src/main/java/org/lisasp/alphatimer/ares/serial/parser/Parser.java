package org.lisasp.alphatimer.ares.serial.parser;

import org.lisasp.alphatimer.api.ares.serial.events.BytesInputEvent;
import org.lisasp.alphatimer.api.ares.serial.events.DataInputEvent;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnknownMessageDroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnstructuredInputDroppedEvent;
import org.lisasp.alphatimer.ares.serial.exceptions.InvalidDataException;

public class Parser {

    private final MessageParser[] parsers = new MessageParser[]{
            new DataHandlingMessage1Parser(), new DataHandlingMessage2Parser(), new PingParser(), new ScoreboardParser()
    };

    public DataInputEvent parse(BytesInputEvent event) {
        if (!event.checkIfMessage()) {
            return new UnstructuredInputDroppedEvent(event.getTimestamp(), event.getCompetition(), event.getData());
        }
        for (MessageParser parser : parsers) {
            if (parser.isKnownMessageFormat(event.getData())) {
                try {
                    return parser.parse(event);
                } catch (InvalidDataException ide) {
                    return parser.createDropMessage(event, ide);
                }
            }
        }

        return new UnknownMessageDroppedEvent(event.getTimestamp(), event.getCompetition(), event.getData());
    }
}
