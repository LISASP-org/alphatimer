package org.lisasp.alphatiming.project.db;

import lombok.extern.slf4j.Slf4j;
import org.lisasp.alphatiming.project.db.TableRebuild.Column;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * A lane belongs to one heat, which holds one lane per number ({@link HeatStore} updates the first lane it finds with a
 * number). Hibernate's SQLite dialect skips both keys, so earlier files may hold lanes no heat shows: these are removed,
 * and of lanes sharing a number the one Hibernate loaded first (the oldest row) is kept.
 */
@Slf4j
final class AddLaneKeys implements MigrationStep {

    @Override
    public void apply(Connection connection) throws SQLException {
        if (!hasTable(connection)) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            int withoutHeat = st.executeUpdate("delete from lane where heat_id is null or heat_id not in (select id from heat)");
            int repeated = st.executeUpdate("delete from lane where exists (select 1 from lane k where k.heat_id = lane.heat_id "
                    + "and k.number = lane.number and k.id < lane.id)");
            if (withoutHeat + repeated > 0) {
                log.warn("Removed {} lanes without heat and {} lanes with a repeated number", withoutHeat, repeated);
            }
        }
        addTo(connection);
    }

    /** Rebuilds the lane table; it must not hold rows that break the keys. */
    static void addTo(Connection connection) throws SQLException {
        List<Column> columns = TableRebuild.columnsOf(connection, "lane")
                                           .stream()
                                           .map(c -> c.name().equals("heat_id") ? c.asNotNull() : c)
                                           .toList();
        TableRebuild.rebuild(connection, "lane", TableRebuild.createSql("lane", columns, List.of("foreign key (\"heat_id\") references heat (\"id\")")),
                             columns.stream().map(Column::name).toList());
        try (Statement st = connection.createStatement()) {
            st.execute("create unique index \"lane_heat_id_number\" on lane (\"heat_id\", \"number\")");
        }
    }

    private static boolean hasTable(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, "lane", new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
