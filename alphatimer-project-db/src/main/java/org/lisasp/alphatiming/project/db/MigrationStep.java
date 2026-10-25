package org.lisasp.alphatiming.project.db;

import java.sql.Connection;
import java.sql.SQLException;

/** One change of the stored schema; runs inside a transaction, before Hibernate sees the file. */
@FunctionalInterface
interface MigrationStep {

    void apply(Connection connection) throws SQLException;
}
