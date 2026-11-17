package org.lisasp.alphatimer.ares.serial.parser;

import org.lisasp.alphatimer.api.ares.serial.Characters;
import org.lisasp.alphatimer.api.ares.serial.events.BytesInputEvent;
import org.lisasp.alphatimer.api.ares.serial.events.DataInputEvent;
import org.lisasp.alphatimer.api.ares.serial.events.messages.Message;
import org.lisasp.alphatimer.api.ares.serial.events.messages.ScoreboardMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.ScoreboardCommand;
import org.lisasp.alphatimer.ares.serial.exceptions.InvalidDataException;

import java.util.Arrays;
import java.util.Optional;

class ScoreboardParser implements MessageParser {

    @Override
    public boolean isKnownMessageFormat(byte[] data) {
        return data.length > 3 && data[1] == Characters.DC4_Command && command(data[2]).isPresent();
    }

    @Override
    public Message parse(BytesInputEvent event) {
        byte[] data = event.getData();
        return new ScoreboardMessage(event.getTimestamp(), event.getCompetition(), command(data[2]).orElseThrow(), data);
    }

    @Override
    public DataInputEvent createDropMessage(BytesInputEvent event, InvalidDataException ide) {
        return null;
    }

    private static Optional<ScoreboardCommand> command(byte value) {
        return Arrays.stream(ScoreboardCommand.values()).filter(c -> c.getValue() == value).findFirst();
    }
}
