package org.lisasp.alphatiming.project.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaMigrationsTest {

    @TempDir
    Path dir;

    private static Connection connect(Path file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file);
    }

    private static List<String> query(Path file, String sql) throws SQLException {
        try (Connection c = connect(file); Statement st = c.createStatement(); var rs = st.executeQuery(sql)) {
            List<String> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(rs.getString(1));
            }
            return rows;
        }
    }

    private static void execute(Path file, String sql) throws SQLException {
        try (Connection c = connect(file); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static MigrationStep insert(String value) {
        return connection -> {
            try (Statement st = connection.createStatement()) {
                st.execute("insert into step (name) values ('" + value + "')");
            }
        };
    }

    private Path fileAtVersion(int version) throws SQLException {
        Path file = dir.resolve("project.sqlite");
        execute(file, "create table step (name varchar(255))");
        execute(file, "pragma user_version = " + version);
        return file;
    }

    @Test
    void theStepsAboveTheVersionOfTheFileRunInOrderAndRaiseTheVersion() throws Exception {
        Path file = fileAtVersion(1);

        SchemaMigrations.migrate(file, List.of(insert("first"), insert("second"), insert("third")));

        assertThat(query(file, "select name from step order by rowid")).containsExactly("second", "third");
        assertThat(query(file, "pragma user_version")).containsExactly("3");
    }

    @Test
    void aFailingStepIsRolledBackAndKeepsTheStepsBeforeIt() throws Exception {
        Path file = fileAtVersion(0);
        MigrationStep failing = connection -> {
            insert("partial").apply(connection);
            throw new SQLException("broken step");
        };

        assertThatThrownBy(() -> SchemaMigrations.migrate(file, List.of(insert("first"), failing)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(file.toString());

        assertThat(query(file, "select name from step order by rowid")).containsExactly("first");
        assertThat(query(file, "pragma user_version")).containsExactly("1");
    }

    @Test
    void aFileOfTheCurrentVersionIsLeftAsItIs() throws Exception {
        Path file = fileAtVersion(SchemaMigrations.CURRENT_VERSION);

        SchemaMigrations.migrate(file);

        assertThat(query(file, "select name from step")).isEmpty();
        assertThat(query(file, "pragma user_version")).containsExactly(String.valueOf(SchemaMigrations.CURRENT_VERSION));
    }

    @Test
    void initializingANewFileGivesItTheCurrentVersion() throws Exception {
        Path file = dir.resolve("new.sqlite");

        SchemaMigrations.initialize(file);

        assertThat(query(file, "pragma user_version")).containsExactly(String.valueOf(SchemaMigrations.CURRENT_VERSION));
    }
}
