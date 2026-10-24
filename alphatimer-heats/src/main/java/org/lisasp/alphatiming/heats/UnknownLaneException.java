package org.lisasp.alphatiming.heats;

public final class UnknownLaneException extends RuntimeException {

    private final int event;
    private final int heat;
    private final int lane;

    UnknownLaneException(int event, int heat, int lane) {
        super(String.format("%d %d: Lane %d not found.", event, heat, lane));
        this.event = event;
        this.heat = heat;
        this.lane = lane;
    }

    public int event() {
        return event;
    }

    public int heat() {
        return heat;
    }

    public int lane() {
        return lane;
    }
}
