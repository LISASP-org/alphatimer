package org.lisasp.alphatiming.heats;

public final class UnknownLaneException extends RuntimeException {

    private final String competition;
    private final int event;
    private final int heat;
    private final int lane;

    UnknownLaneException(String competition, int event, int heat, int lane) {
        super(String.format("%s %d %d: Lane %d not found.", competition, event, heat, lane));
        this.competition = competition;
        this.event = event;
        this.heat = heat;
        this.lane = lane;
    }

    public String competition() {
        return competition;
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
