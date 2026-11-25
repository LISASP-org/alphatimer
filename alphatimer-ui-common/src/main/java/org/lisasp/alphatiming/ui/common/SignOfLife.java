package org.lisasp.alphatiming.ui.common;

import org.lisasp.basics.jre.date.DateTimeFacade;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * When the timekeeping system last showed it is alive: by a ping (about one per second, if switched on in ARES) or by
 * any valid message. Received on the reader thread, read by the front ends.
 */
public final class SignOfLife {

    static final Duration STREAM_SILENT_AFTER = Duration.ofSeconds(5);
    public static final Duration MESSAGE_SHOWN = Duration.ofMillis(100);

    private final DateTimeFacade time;
    private volatile LocalDateTime last;
    private volatile LocalDateTime lastMessage;

    public SignOfLife(DateTimeFacade time) {
        this.time = time;
    }

    public void received() {
        last = time.now();
    }

    public void forget() {
        last = null;
        lastMessage = null;
    }

    public void messageReceived() {
        LocalDateTime now = time.now();
        lastMessage = now;
        last = now;
    }

    public boolean isShowingMessage() {
        LocalDateTime at = lastMessage;
        return at != null && !time.now().isAfter(at.plus(MESSAGE_SHOWN));
    }

    public boolean isRecent(Duration silentAfter) {
        LocalDateTime at = last;
        return at != null && !time.now().isAfter(at.plus(silentAfter));
    }
}
