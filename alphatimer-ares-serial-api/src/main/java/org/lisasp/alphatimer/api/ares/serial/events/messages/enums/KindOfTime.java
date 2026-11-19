package org.lisasp.alphatimer.api.ares.serial.events.messages.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum KindOfTime {
    Start('S'),
    SplitTime('I'),
    Finish('A'),
    TakeOverTime('D'),
    ReactionTimeAtStart('R'),
    Empty(' '),
    Backup('B');

    private final char value;
}
