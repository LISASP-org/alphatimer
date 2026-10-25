package org.lisasp.alphatiming.ui;

import java.util.stream.Collectors;

final class ToolTips {

    private ToolTips() {
    }

    /** Multi-line text as a tool tip: Swing shows only one line of plain text and HTML drops leading spaces. */
    static String of(String text) {
        if (text.isEmpty()) {
            return null;
        }
        return text.lines().map(ToolTips::line).collect(Collectors.joining("<br>", "<html>", "</html>"));
    }

    private static String line(String line) {
        String escaped = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String content = escaped.stripLeading();
        return "&nbsp;".repeat(escaped.length() - content.length()) + content;
    }
}
