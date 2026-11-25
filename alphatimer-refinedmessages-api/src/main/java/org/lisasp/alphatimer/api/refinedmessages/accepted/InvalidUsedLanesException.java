package org.lisasp.alphatimer.api.refinedmessages.accepted;

public final class InvalidUsedLanesException extends RuntimeException {

    private final String usedLanes;

    InvalidUsedLanesException(String usedLanes) {
        super("The used lanes of ARES are ten flags '0' or '1': " + usedLanes);
        this.usedLanes = usedLanes;
    }

    public String usedLanes() {
        return usedLanes;
    }
}
