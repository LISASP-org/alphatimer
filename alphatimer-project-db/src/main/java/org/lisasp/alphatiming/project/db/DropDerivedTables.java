package org.lisasp.alphatiming.project.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Heats and legacy lane times are rebuilt from the recorded messages, so their tables are no longer stored. */
final class DropDerivedTables implements MigrationStep {

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("drop table if exists lane");
            st.execute("drop table if exists heat");
            st.execute("drop table if exists lanetime");
        }
    }
}
