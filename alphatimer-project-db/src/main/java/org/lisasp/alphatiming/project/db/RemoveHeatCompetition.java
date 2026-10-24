package org.lisasp.alphatiming.project.db;

import org.lisasp.alphatiming.project.db.TableRebuild.Column;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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

    @Override
    public void apply(Connection connection) throws SQLException {
        List<Column> columns = TableRebuild.columnsOf(connection, "heat");
        if (columns.stream().noneMatch(c -> c.name().equals("competition"))) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute("delete from lane where heat_id in (" + SUPERSEDED_HEATS + ")");
            st.execute("delete from heat where id in (" + SUPERSEDED_HEATS + ")");
        }
        List<Column> kept = columns.stream().filter(c -> !c.name().equals("competition")).toList();
        TableRebuild.rebuild(connection, "heat", TableRebuild.createSql("heat", kept, List.of()), kept.stream().map(Column::name).toList());
        try (Statement st = connection.createStatement()) {
            st.execute("create unique index \"heat_event_heatNumber\" on heat (\"event\", \"heatNumber\")");
        }
    }
}
