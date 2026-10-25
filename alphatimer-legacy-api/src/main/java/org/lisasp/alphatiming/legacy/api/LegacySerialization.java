package org.lisasp.alphatiming.legacy.api;

import java.io.IOException;
import java.io.InputStream;

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

    public static Heat[] fromXML(InputStream in) throws IOException {
        return LegacyXmlReader.read(in);
    }

    private static String addPrefixToEachLine(String text) {
        return String.join("\n  ", text.split("\n"));
    }
}
