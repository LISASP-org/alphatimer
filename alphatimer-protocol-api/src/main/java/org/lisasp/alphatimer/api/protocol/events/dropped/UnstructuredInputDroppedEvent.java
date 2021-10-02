package org.lisasp.alphatimer.api.protocol.events.dropped;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.lisasp.alphatimer.api.protocol.data.Bytes;

import java.time.LocalDateTime;

@Value
@RequiredArgsConstructor
public class UnstructuredInputDroppedEvent implements InputDataDroppedEvent {
    private final LocalDateTime timestamp;
    private final String competition;
    private final Bytes data;

    public UnstructuredInputDroppedEvent(LocalDateTime timestamp, String competition, byte[] data) {
        this(timestamp, competition, new Bytes(data));
    }
}