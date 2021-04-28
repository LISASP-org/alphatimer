package org.lisasp.alphatimer.protocol;

import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import org.lisasp.alphatimer.api.protocol.events.DataInputEvent;
import org.lisasp.alphatimer.api.protocol.events.dropped.UnstructuredInputDroppedEvent;
import org.lisasp.alphatimer.protocol.parser.Parser;

import java.util.Optional;

@RequiredArgsConstructor
class MessageExtractor {

    private final Parser parser = new Parser();

    private final ByteStorage storage = new ByteStorage();

    @Synchronized("storage")
    Optional<DataInputEvent> put(byte entry) {
        storage.append(entry);
        return parseMessage().or(() -> cleanup());
    }

    private Optional<DataInputEvent> cleanup() {
        if (!storage.endsWithEndOfMessage()) {
            return Optional.empty();
        }
        return Optional.of(new UnstructuredInputDroppedEvent(storage.extractByteArray()));
    }

    private Optional<DataInputEvent> parseMessage() {
        if (storage.endsWithStartOfMessage()) {
            return dropUnstructuredInput(storage.extractAllButLatestEntry());
        }
        if (storage.isFull()) {
            return dropUnstructuredInput(storage.extractAllButLatestEntry());
        }
        if (!storage.isMessage()) {
            return Optional.empty();
        }
        return Optional.of(parser.parse(storage.extractByteArray()));
    }

    private Optional<DataInputEvent> dropUnstructuredInput(byte[] data) {
        if (data.length > 0) {
            return Optional.of(new UnstructuredInputDroppedEvent(data));
        }
        return Optional.empty();
    }

    Optional<DataInputEvent> shutdown() {
        return dropUnstructuredInput(storage.extractByteArray());
    }
}
