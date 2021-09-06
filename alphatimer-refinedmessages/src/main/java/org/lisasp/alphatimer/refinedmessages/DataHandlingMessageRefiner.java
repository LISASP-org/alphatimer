package org.lisasp.alphatimer.refinedmessages;

import org.lisasp.alphatimer.api.protocol.DataHandlingMessageListener;
import org.lisasp.alphatimer.api.protocol.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessageListener;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedUnknownMessage;
import org.lisasp.alphatimer.messaging.CollectingConsumer;
import org.lisasp.alphatimer.messaging.Notifier;

public class DataHandlingMessageRefiner implements DataHandlingMessageListener {

    private final Notifier<RefinedMessage> notifier = new Notifier<>();

    private final Parser[] parsers = new Parser[]{
            new ReadyToStartParser(),
            new OfficialEndParser(),
            new TimeParser(),
            new StartParser(),
            new DidNotStartParser(),
            new DidNotFinishParser(),
            new TakeOverTimeParser(),
            new UsedLanesParser()
    };

    @Override
    public void accept(final DataHandlingMessage message) {
        parse(message).forEach(m -> notifier.accept(m));
    }

    private CollectingConsumer<RefinedMessage> parse(DataHandlingMessage message) {
        CollectingConsumer<RefinedMessage> collector = new CollectingConsumer<>();

        for (Parser parser : parsers) {
            parser.accept(message, collector);
        }

        if (collector.isEmpty()) {
            collector.accept(new DroppedUnknownMessage(message));
        }

        return collector;
    }

    public void register(RefinedMessageListener listener) {
        notifier.register(listener);
    }
}
