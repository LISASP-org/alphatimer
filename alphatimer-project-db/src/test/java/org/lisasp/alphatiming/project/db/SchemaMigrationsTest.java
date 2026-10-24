package org.lisasp.alphatiming.project.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    private Path fileOfTheFirstVersion() throws SQLException {
        Path file = dir.resolve("old.sqlite");
        try (Connection c = connect(file); Statement st = c.createStatement()) {
            st.execute("create table heat (id integer, competition varchar(255), event integer not null, heatNumber integer not null, "
                    + "status varchar(255) check (status in ('Open','Started','Finished')), primary key (id), "
                    + "constraint UK_heat unique (competition, event, heatNumber))");
            st.execute("create table lane (id integer, number integer not null, "
                    + "penalty varchar(255) check (penalty in ('None','DidNotStart','DidNotFinish')), "
                    + "status varchar(255) check (status in ('NotUsed','Used')), heat_id bigint, primary key (id), "
                    + "constraint FK_lane_heat foreign key (heat_id) references heat)");
            st.execute("create index IX_lane_heat on lane (heat_id)");
            st.execute("insert into heat (id, competition, event, heatNumber, status) values (7, 'WK', 1, 2, 'Finished')");
            st.execute("insert into lane (id, number, penalty, status, heat_id) values (3, 4, 'DidNotStart', 'Used', 7)");
        }
        return file;
    }

    private static Connection connect(Path file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file);
    }

    private static List<String> query(Path file, String sql) throws SQLException {
        try (Connection c = connect(file); Statement st = c.createStatement(); var rs = st.executeQuery(sql)) {
            int columns = rs.getMetaData().getColumnCount();
            List<String> rows = new ArrayList<>();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columns; i++) {
                    row.add(rs.getString(i));
                }
                rows.add(String.join("|", row));
            }
            return rows;
        }
    }

    private static void execute(Path file, String sql) throws SQLException {
        try (Connection c = connect(file); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    @Test
    void migratingAFileOfTheFirstVersionRemovesItsCheckConstraintsAndKeepsDataIndexesAndKeys() throws Exception {
        Path file = fileOfTheFirstVersion();

        SchemaMigrations.migrate(file);

        assertThat(query(file, "select sql from sqlite_master where sql like '%check%'")).isEmpty();
        assertThat(query(file, "select id, competition, event, heatNumber, status from heat")).containsExactly("7|WK|1|2|Finished");
        assertThat(query(file, "select id, number, penalty, status, heat_id from lane")).containsExactly("3|4|DidNotStart|Used|7");
        assertThat(query(file, "select name from sqlite_master where type = 'index' and sql is not null")).containsExactly("IX_lane_heat");
        assertThat(query(file, "select \"table\", \"from\", \"to\" from pragma_foreign_key_list('lane')")).containsExactly("heat|heat_id|null");
        assertThat(query(file, "pragma user_version")).containsExactly(String.valueOf(SchemaMigrations.CURRENT_VERSION));

        execute(file, "insert into heat (id, competition, event, heatNumber, status) values (8, 'WK', 1, 3, 'SomethingNew')");
        execute(file, "insert into lane (id, number, penalty, status, heat_id) values (4, 5, 'NewPenalty', 'NewStatus', 8)");
        assertThatThrownBy(() -> execute(file, "insert into heat (competition, event, heatNumber) values ('WK', 1, 2)"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("UNIQUE");
    }

    @Test
    void aStepThatAlreadyRanIsNotRepeated() throws Exception {
        Path file = dir.resolve("migrated.sqlite");
        execute(file, "create table heat (id integer, status varchar(255) check (status in ('Open')), primary key (id))");
        execute(file, "pragma user_version = " + SchemaMigrations.CURRENT_VERSION);

        SchemaMigrations.migrate(file);

        assertThat(query(file, "select sql from sqlite_master where name = 'heat'")).singleElement().asString().contains("check");
    }

    @Test
    void initializingANewFileGivesItTheCurrentVersion() throws Exception {
        Path file = dir.resolve("new.sqlite");

        SchemaMigrations.initialize(file);

        assertThat(query(file, "pragma user_version")).containsExactly(String.valueOf(SchemaMigrations.CURRENT_VERSION));
    }
}
