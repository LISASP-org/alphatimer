package org.lisasp.alphatiming.project.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Earlier versions let Hibernate write a CHECK with the allowed values for every enum column; such a file would reject
 * every value added to an enum later. The checks are removed, the stored names stay as they are.
 */
final class RemoveCheckConstraints implements MigrationStep {

    private static final Pattern CHECK = Pattern.compile("(?:\\s*,\\s*|\\s+)check\\s*\\(", Pattern.CASE_INSENSITIVE);

    @Override
    public void apply(Connection connection) throws SQLException {
        for (String table : tables(connection)) {
            String createSql = TableRebuild.createSqlOf(connection, table);
            String withoutChecks = withoutCheckConstraints(createSql);
            if (!withoutChecks.equals(createSql)) {
                TableRebuild.rebuild(connection, table, withoutChecks);
            }
        }
    }

    private static List<String> tables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("select name from sqlite_master where type = 'table' and name not like 'sqlite\\_%' escape '\\'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    static String withoutCheckConstraints(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        Matcher check = CHECK.matcher(sql);
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (isQuote(c)) {
                int end = endOfQuoted(sql, i);
                result.append(sql, i, end);
                i = end;
            } else if (check.region(i, sql.length()).lookingAt()) {
                i = endOfParentheses(sql, check.end());
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    private static boolean isQuote(char c) {
        return c == '\'' || c == '"' || c == '`' || c == '[';
    }

    private static int endOfQuoted(String sql, int start) {
        char close = sql.charAt(start) == '[' ? ']' : sql.charAt(start);
        int i = start + 1;
        while (i < sql.length()) {
            if (sql.charAt(i) == close) {
                if (close != ']' && i + 1 < sql.length() && sql.charAt(i + 1) == close) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }

    private static int endOfParentheses(String sql, int start) {
        int depth = 1;
        int i = start;
        while (i < sql.length() && depth > 0) {
            char c = sql.charAt(i);
            if (isQuote(c)) {
                i = endOfQuoted(sql, i);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            i++;
        }
        return i;
    }
}
