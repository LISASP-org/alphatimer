package org.lisasp.alphatiming.legacy.server;

import org.lisasp.alphatiming.legacy.server.dto.Heat;

public class LegacySerialization {

    public static String toXML(Heat[] heats) {
        if (heats.length == 0) {
            return "<AlphaServer.Heat-array/>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<AlphaServer.Heat-array>\n");
        for (Heat heat : heats) {
            sb.append("  ");
            sb.append(addPrefixToEachLine(heat.toXML()));
            sb.append("\n");
        }
        sb.append("</AlphaServer.Heat-array>");
        return sb.toString();
    }

    private static String addPrefixToEachLine(String text) {
        return String.join("\n  ", text.split("\n"));
    }
}
