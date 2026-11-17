package org.lisasp.alphatimer.api.ares.serial.events.messages.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum TimeMarker {
    Empty(" "),
    Plus("+"),
    Minus("-"),
    DidNotStart("DNS"),
    DidNotFinish("DNF"),
    Disqualified("DSQ");

    private final String value;
}
