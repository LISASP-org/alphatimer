package de.dennisfabri.alphatimer.api.events.dropped;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class DataHandlingMessage2DroppedEvent implements InputDataDroppedEvent {

    private final String message;
    private final byte[] data;
}
