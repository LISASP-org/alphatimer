package org.lisasp.alphatimer.protocol;

import org.lisasp.alphatimer.api.protocol.DataHandlingMessageListener;
import org.lisasp.alphatimer.api.protocol.DataInputEventListener;
import org.lisasp.alphatimer.api.protocol.events.DataInputEvent;
import org.lisasp.alphatimer.api.protocol.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.protocol.events.messages.DataHandlingMessage1;
import org.lisasp.alphatimer.api.protocol.events.messages.DataHandlingMessage2;
import org.lisasp.alphatimer.api.protocol.events.messages.Ping;
import lombok.NoArgsConstructor;
import lombok.Synchronized;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class DataHandlingMessageAggregator implements DataInputEventListener {

    private DataHandlingMessage1 message1 = null;

    private final List<DataHandlingMessageListener> listeners = new ArrayList<>();

    public DataHandlingMessageAggregator(DataHandlingMessageListener listener) {
        register(listener);
    }

    @Synchronized("listeners")
    public void register(DataHandlingMessageListener listener) {
        listeners.add(listener);
    }

    private void notify(DataHandlingMessage message) {
        listeners.forEach(l -> l.accept(message));
    }

    @Override
    @Synchronized("listeners")
    public void accept(DataInputEvent event) {
        if (event instanceof DataHandlingMessage1) {
            message1 = (DataHandlingMessage1) event;
            return;
        }
        if (event instanceof DataHandlingMessage2 && message1 != null) {
            DataHandlingMessage2 message2 = (DataHandlingMessage2) event;
            notify(new DataHandlingMessage(
                    message1.getOriginal(),
                    message2.getOriginal(),
                    message1.getMessageType(),
                    message1.getKindOfTime(),
                    message1.getTimeType(),
                    message1.getUsedLanes(),
                    message1.getLapCount(),
                    message1.getEvent(),
                    message1.getHeat(),
                    message1.getRank(),
                    message1.getRankInfo(),
                    message2.getLane(),
                    message2.getCurrentLap(),
                    message2.getTimeInMillis(),
                    message2.getTimeInfo(),
                    message2.getTimeMarker()));
        }
        if (event instanceof Ping) {
            // Do not reset message1 on ping
            return;
        }
        message1 = null;
    }
}