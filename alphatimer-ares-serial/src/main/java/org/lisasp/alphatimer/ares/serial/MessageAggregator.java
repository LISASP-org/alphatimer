package org.lisasp.alphatimer.ares.serial;

import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageAggregator;
import org.lisasp.alphatimer.api.ares.serial.DataHandlingMessageListener;
import org.lisasp.alphatimer.api.ares.serial.events.DataInputEvent;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage1;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage2;
import org.lisasp.alphatimer.api.ares.serial.events.messages.Ping;
import org.lisasp.alphatimer.api.ares.serial.events.messages.ScoreboardMessage;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class MessageAggregator implements DataHandlingMessageAggregator {

    private DataHandlingMessage1 message1 = null;

    private final List<DataHandlingMessageListener> listeners = new ArrayList<>();

    @Override
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
                    message2.getTimestamp(),
                    message2.getCompetition(),
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
        if (event instanceof Ping || event instanceof ScoreboardMessage) {
            return;
        }
        message1 = null;
    }
}
