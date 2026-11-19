package org.lisasp.alphatiming.ui.core.recording;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageListener;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;

import java.util.List;
import java.util.function.Consumer;

/** Feeds stored messages through the same steps as live ones, so the heats and lane times can be rebuilt from them. */
@Slf4j
final class MessageReplay {

    private MessageReplay() {
    }

    static void replay(List<DataHandlingMessage> messages, DataHandlingMessageListener legacy, Consumer<RefinedMessage> heats) {
        DataHandlingMessageRefiner refiner = new DataHandlingMessageRefiner();
        refiner.register(heats::accept);
        for (DataHandlingMessage message : messages) {
            skippingFailure(message, legacy::accept);
            skippingFailure(message, refiner::accept);
        }
    }

    /** The live pipeline gives each consumer its own failure handling; a replay must treat a message the same way. */
    private static void skippingFailure(DataHandlingMessage message, DataHandlingMessageListener consumer) {
        try {
            consumer.accept(message);
        } catch (RuntimeException ex) {
            log.warn("Could not replay stored message of event {}, heat {}, lane {}.", message.getEvent(), message.getHeat(), message.getLane(), ex);
        }
    }
}
