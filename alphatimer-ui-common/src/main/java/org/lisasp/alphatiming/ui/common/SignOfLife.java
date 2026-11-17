package org.lisasp.alphatiming.ui.common;

import org.lisasp.basics.jre.date.DateTimeFacade;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * When the timekeeping system last showed it is alive: by a ping (about one per second, if switched on in ARES) or by
 * any valid message. Received on the reader thread, read by the front ends.
 */
public final class SignOfLife {

    static final Duration RECENT = Duration.ofSeconds(5);

    private final DateTimeFacade time;
    private volatile LocalDateTime last;

    public SignOfLife(DateTimeFacade time) {
        this.time = time;
    }

    public void received() {
        last = time.now();
    }

    public void forget() {
        last = null;
    }

    public boolean isRecent() {
        LocalDateTime at = last;
        return at != null && !time.now().isAfter(at.plus(RECENT));
    }
}
