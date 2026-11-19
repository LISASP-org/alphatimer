package org.lisasp.alphatimer.api.ares.serial.events.messages.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ScoreboardCommand {
    RunningTime((byte) 'R'),
    ResultLine((byte) 'S'),
    UnknownValueL((byte) 'L');

    private final byte value;
}
