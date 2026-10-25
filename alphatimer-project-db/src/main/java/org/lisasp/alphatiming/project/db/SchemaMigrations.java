package org.lisasp.alphatiming.project.db;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Brings the schema of a project file to the current version before Hibernate opens it. The version is SQLite's
 * {@code user_version}: the number of steps applied. There are no steps yet: the files of version 1 are the baseline.
 */
@Slf4j
final class SchemaMigrations {

    private static final List<MigrationStep> STEPS = List.of();

    static final int CURRENT_VERSION = STEPS.size();

    private SchemaMigrations() {
    }

    /** A new file gets its schema from Hibernate, which matches the current version, so no step must ever run on it. */
    static void initialize(Path file) throws IOException {
        try (Connection connection = connect(file); Statement st = connection.createStatement()) {
            st.execute("pragma user_version = " + CURRENT_VERSION);
        } catch (SQLException ex) {
            throw new IOException("Could not initialize the schema version of " + file, ex);
        }
    }

    static void migrate(Path file) throws IOException {
        migrate(file, STEPS);
    }

    static void migrate(Path file, List<MigrationStep> steps) throws IOException {
        try (Connection connection = connect(file)) {
            int version = versionOf(connection);
            for (int step = version; step < steps.size(); step++) {
                apply(connection, steps, step);
            }
        } catch (SQLException ex) {
            throw new IOException("Could not migrate " + file, ex);
        }
    }

    private static Connection connect(Path file) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("pragma foreign_keys = off");
        }
        return connection;
    }

    private static int versionOf(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("pragma user_version")) {
            return rs.getInt(1);
        }
    }

    private static void apply(Connection connection, List<MigrationStep> steps, int step) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement st = connection.createStatement()) {
            steps.get(step).apply(connection);
            st.execute("pragma user_version = " + (step + 1));
            connection.commit();
            log.info("Migrated project schema to version {}", step + 1);
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
