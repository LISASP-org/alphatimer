package org.lisasp.alphatiming.project.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A heat is identified by event and heat number; the competition is the one in the settings. Earlier versions stored a
 * competition tag per heat, and the tag of new heats changed between versions, so a file can hold the same heat under
 * two tags (no earlier file has a unique key on the heat: Hibernate's SQLite dialect skipped it). Only the most recently
 * changed of them is kept, with its lanes. The unique key is the index a new file gets.
 */
final class RemoveHeatCompetition implements MigrationStep {

    private static final String SUPERSEDED_HEATS = "select h.id from heat h where exists (select 1 from heat n where n.event = h.event "
            + "and n.heatNumber = h.heatNumber and (n.changeSeq > h.changeSeq or (n.changeSeq = h.changeSeq and n.id > h.id)))";

    private record Column(String name, String type, boolean notNull, String defaultValue, int primaryKeyIndex) {

        String definition() {
            return TableRebuild.quote(name) + (type.isEmpty() ? "" : " " + type) + (notNull ? " not null" : "")
                    + (defaultValue == null ? "" : " default " + defaultValue);
        }
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        List<Column> columns = columnsOfHeat(connection);
        if (columns.stream().noneMatch(c -> c.name().equals("competition"))) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute("delete from lane where heat_id in (" + SUPERSEDED_HEATS + ")");
            st.execute("delete from heat where id in (" + SUPERSEDED_HEATS + ")");
        }
        List<Column> kept = columns.stream().filter(c -> !c.name().equals("competition")).toList();
        TableRebuild.rebuild(connection, "heat", createSql(kept), kept.stream().map(Column::name).toList());
        try (Statement st = connection.createStatement()) {
            st.execute("create unique index \"heat_event_heatNumber\" on heat (\"event\", \"heatNumber\")");
        }
    }

    private static List<Column> columnsOfHeat(Connection connection) throws SQLException {
        List<Column> columns = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("select name, type, \"notnull\", dflt_value, pk from pragma_table_info('heat') order by cid")) {
            while (rs.next()) {
                columns.add(new Column(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getString(4), rs.getInt(5)));
            }
        }
        return columns;
    }

    private static String createSql(List<Column> columns) {
        List<String> parts = new ArrayList<>(columns.stream().map(Column::definition).toList());
        List<String> primaryKey = columns.stream()
                                         .filter(c -> c.primaryKeyIndex() > 0)
                                         .sorted(Comparator.comparingInt(Column::primaryKeyIndex))
                                         .map(c -> TableRebuild.quote(c.name()))
                                         .toList();
        if (!primaryKey.isEmpty()) {
            parts.add("primary key (" + String.join(", ", primaryKey) + ")");
        }
        return "create table heat (" + String.join(", ", parts) + ")";
    }
}
