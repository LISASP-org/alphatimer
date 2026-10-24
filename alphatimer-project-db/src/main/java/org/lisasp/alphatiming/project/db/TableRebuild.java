package org.lisasp.alphatiming.project.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces the definition of a table, which SQLite's ALTER TABLE cannot do for constraints or removed columns: creates
 * the new table, copies the rows, drops the old one, renames the new one and recreates the indexes and triggers.
 * Requires {@code foreign_keys = off}, otherwise dropping a referenced table would delete or reject the referencing rows.
 */
final class TableRebuild {

    private static final Pattern TABLE_NAME = Pattern.compile("^(\\s*create\\s+table\\s+)(\"[^\"]*\"|`[^`]*`|\\[[^]]*]|[^\\s(]+)",
            Pattern.CASE_INSENSITIVE);

    private TableRebuild() {
    }

    static String createSqlOf(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select sql from sqlite_master where type = 'table' and name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No table " + table);
                }
                return rs.getString(1);
            }
        }
    }

    /** {@code createSql} defines the table under its own name, with the columns of the old table in the same order. */
    static void rebuild(Connection connection, String table, String createSql) throws SQLException {
        rebuild(connection, table, createSql, "*", "");
    }

    /** {@code createSql} defines the table under its own name, with at least the given columns. */
    static void rebuild(Connection connection, String table, String createSql, List<String> columns) throws SQLException {
        String quoted = String.join(", ", columns.stream().map(TableRebuild::quote).toList());
        rebuild(connection, table, createSql, quoted, " (" + quoted + ")");
    }

    private static void rebuild(Connection connection, String table, String createSql, String selected, String inserted) throws SQLException {
        String temporary = table + "_rebuild";
        List<String> dependents = dependentObjectsOf(connection, table);
        try (Statement st = connection.createStatement()) {
            st.execute(withTableName(createSql, temporary));
            st.execute("insert into " + quote(temporary) + inserted + " select " + selected + " from " + quote(table));
            st.execute("drop table " + quote(table));
            st.execute("alter table " + quote(temporary) + " rename to " + quote(table));
            for (String sql : dependents) {
                st.execute(sql);
            }
        }
    }

    private static List<String> dependentObjectsOf(Connection connection, String table) throws SQLException {
        List<String> sql = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "select sql from sqlite_master where type in ('index', 'trigger') and tbl_name = ? and sql is not null")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sql.add(rs.getString(1));
                }
            }
        }
        return sql;
    }

    private static String withTableName(String createSql, String name) throws SQLException {
        Matcher m = TABLE_NAME.matcher(createSql);
        if (!m.find()) {
            throw new SQLException("Unexpected table definition: " + createSql);
        }
        return m.group(1) + quote(name) + createSql.substring(m.end());
    }

    static String quote(String name) {
        return '"' + name.replace("\"", "\"\"") + '"';
    }
}
