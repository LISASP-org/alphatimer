package de.dennisfabri.alphatimer.api.protocol.events.dropped

import de.dennisfabri.alphatimer.api.protocol.data.Bytes

data class UnknownMessageDroppedEvent(private val data: Bytes) : InputDataDroppedEvent {
    constructor(data: ByteArray) : this(Bytes(data))
}
