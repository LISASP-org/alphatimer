package org.lisasp.alphatiming.legacy.api;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Arrays;

@Getter
@EqualsAndHashCode
public class Lane {

    private final int laneindex;

    private long[] times = new long[0];

    private LaneStatus[] stati = new LaneStatus[0];

    public Lane(int index) {
        laneindex = index;
    }

    public void store(long time, LaneStatus status) {
        long[] newTimes = Arrays.copyOf(times, times.length + 1);
        LaneStatus[] newStatus = Arrays.copyOf(stati, stati.length + 1);
        newTimes[times.length] = time;
        newStatus[stati.length] = status;
        times = newTimes;
        stati = newStatus;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(laneindex);
        sb.append(":");
        for (int x = 0; x < times.length; x++) {
            long time = times[x];
            LaneStatus status = stati[x];

            sb.append(" ");
            sb.append(zeitString(time));
            sb.append(" (");
            sb.append(status);
            sb.append(")");
        }
        return sb.toString();
    }

    private static String zeitString(final long zeit) {
        long min = zeit / 60000;
        int sec = (int) ((zeit % 60000) / 1000);
        int millis = (int) (zeit % 1000);
        StringBuilder s = new StringBuilder();
        s.append(min);
        s.append(':');
        if (sec < 10) {
            s.append('0');
        }
        s.append(sec);
        s.append(',');
        if (millis < 100) {
            s.append('0');
        }
        if (millis < 10) {
            s.append('0');
        }
        s.append(millis);
        return s.toString();
    }

    String toXML() {
        return String.format("      <AlphaServer.Lane laneindex=\"%d\">\n%s\n%s\n      </AlphaServer.Lane>", laneindex, timesToXML(), statiToXML());
    }

    private String timesToXML() {
        if (times.length == 0) {
            return "        <times/>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("        <times>\n");
        for (int x=0;x<times.length;x++) {
            sb.append(String.format("          <long>%d</long>\n", times[x]));
        }
        sb.append("        </times>");
        return sb.toString();
    }

    private String statiToXML() {
        if (stati.length == 0) {
            return "        <stati/>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("        <stati>\n");
        for (int x=0;x<stati.length;x++) {
            sb.append(String.format("          <AlphaServer.LaneStatus>%s</AlphaServer.LaneStatus>\n", stati[x].toString()));
        }
        sb.append("        </stati>");
        return sb.toString();
    }
}
