package org.lisasp.alphatiming.project.db;

import org.lisasp.alphatiming.project.db.TableRebuild.Column;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * A lane belongs to one heat, which holds one lane per number ({@link HeatStore} updates the first lane it finds with a
 * number). Hibernate's SQLite dialect skips both keys, so the lane table is rebuilt with them.
 */
final class LaneKeys {

    private LaneKeys() {
    }

    /** The lane table must not hold rows that break the keys. */
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
}
