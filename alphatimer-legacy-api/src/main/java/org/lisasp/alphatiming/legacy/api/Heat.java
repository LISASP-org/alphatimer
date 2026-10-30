package org.lisasp.alphatiming.legacy.api;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode
@Getter
@Setter
@ToString
public class Heat implements Comparable<Heat> {

    private final int event;
    private final int heat;
    private String id;

    private final HashMap<Integer, Lane> lanes = new HashMap<>();

    public Heat(String id, int event, int numberOfHeat) {
        this.event = event;
        this.heat = numberOfHeat;
        this.id = id;
    }

    public void store(int laneNumber, long time, LaneStatus status) {
        synchronized (lanes) {
            for (int i = 0; i <= laneNumber; i++) {
                lanes.computeIfAbsent(i, j -> new Lane(j));
            }
            lanes.computeIfAbsent(laneNumber, j -> new Lane(j)).store(time, status);
        }
    }

    public String computeKey() {
        return computeKey(getEvent(), getHeat());
    }

    public static String computeKey(int event, int heat) {
        return String.format("%d-%d", event, heat);
    }

    public HashMap<Integer, Lane> getLanes() {
        return lanes;
    }

    @SuppressWarnings("unchecked")
    public String toXML() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<AlphaServer.Heat event=\"%d\" heat=\"%d\" id=\"%s\">\n", event, heat, id));
        sb.append("  <lanes>\n");
        for (Map.Entry<Integer, Lane> entry : lanes.entrySet().stream().sorted((l1, l2) -> l1.getKey() - l2.getKey()).toArray(Map.Entry[]::new)) {
            sb.append(String.format("    <entry>\n      <int>%d</int>\n", entry.getKey()));
            sb.append(entry.getValue().toXML());
            sb.append("\n    </entry>\n");
        }
        sb.append("  </lanes>\n");
        sb.append("</AlphaServer.Heat>");
        return sb.toString();
    }

    @Override
    public int compareTo(Heat o) {
        if (o == null) {
            return 1;
        }

        if (event != o.getEvent()) {
            return event - o.getEvent();
        }
        return heat - o.getHeat();
    }
}
