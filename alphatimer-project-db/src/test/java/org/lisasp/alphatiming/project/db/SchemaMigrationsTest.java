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
            st.execute("create table heat (id integer, changeSeq bigint not null, competition varchar(255), event integer not null, heatNumber integer not null, "
                    + "status varchar(255) check (status in ('Open','Started','Finished')), primary key (id), "
                    + "constraint UK_heat unique (competition, event, heatNumber))");
            st.execute("create table lane (id integer, number integer not null, "
                    + "penalty varchar(255) check (penalty in ('None','DidNotStart','DidNotFinish')), "
                    + "status varchar(255) check (status in ('NotUsed','Used')), heat_id bigint, primary key (id), "
                    + "constraint FK_lane_heat foreign key (heat_id) references heat)");
            st.execute("create index IX_lane_heat on lane (heat_id)");
            st.execute("insert into heat (id, changeSeq, competition, event, heatNumber, status) values (7, 1, 'WK', 1, 2, 'Finished')");
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
    void migratingAFileOfTheFirstVersionRemovesItsCheckConstraintsAndTheHeatCompetitionAndKeepsDataIndexesAndKeys() throws Exception {
        Path file = fileOfTheFirstVersion();

        SchemaMigrations.migrate(file);

        assertThat(query(file, "select name from pragma_table_info('heat')")).containsExactly("id", "changeSeq", "event", "heatNumber", "status");
        assertThat(query(file, "select sql from sqlite_master where sql like '%check%'")).isEmpty();
        assertThat(query(file, "select id, changeSeq, event, heatNumber, status from heat")).containsExactly("7|1|1|2|Finished");
        assertThat(query(file, "select id, number, penalty, status, heat_id from lane")).containsExactly("3|4|DidNotStart|Used|7");
        assertThat(query(file, "select name from sqlite_master where type = 'index' and sql is not null"))
                .containsExactlyInAnyOrder("IX_lane_heat", "heat_event_heatNumber", "lane_heat_id_number");
        assertThat(query(file, "select \"table\", \"from\", \"to\" from pragma_foreign_key_list('lane')")).containsExactly("heat|heat_id|id");
        assertThat(query(file, "pragma user_version")).containsExactly(String.valueOf(SchemaMigrations.CURRENT_VERSION));

        execute(file, "insert into heat (id, changeSeq, event, heatNumber, status) values (8, 2, 1, 3, 'SomethingNew')");
        execute(file, "insert into lane (id, number, penalty, status, heat_id) values (4, 5, 'NewPenalty', 'NewStatus', 8)");
        assertThatThrownBy(() -> execute(file, "insert into heat (changeSeq, event, heatNumber) values (3, 1, 2)"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("UNIQUE");
    }

    @Test
    void aHeatStoredUnderTwoCompetitionTagsKeepsOnlyItsMostRecentlyChangedRowWithItsLanes() throws Exception {
        Path file = dir.resolve("tagged.sqlite");
        execute(file, "create table heat (id integer, changeSeq bigint not null, competition varchar(255), event integer not null, "
                + "heatNumber integer not null, primary key (id))");
        execute(file, "create table lane (id integer, number integer not null, heat_id bigint, primary key (id))");
        execute(file, "insert into heat (id, changeSeq, competition, event, heatNumber) values (7, 3, 'TestWK', 1, 2), (8, 4, 'TestWK', 1, 3), (9, 5, '', 1, 2)");
        execute(file, "insert into lane (id, number, heat_id) values (1, 1, 7), (2, 2, 7), (3, 1, 9), (4, 2, 9), (5, 1, 8)");

        SchemaMigrations.migrate(file);

        assertThat(query(file, "select id, changeSeq, event, heatNumber from heat order by id")).containsExactly("8|4|1|3", "9|5|1|2");
        assertThat(query(file, "select id, number, heat_id from lane order by id")).containsExactly("3|1|9", "4|2|9", "5|1|8");
        assertThatThrownBy(() -> execute(file, "insert into heat (changeSeq, event, heatNumber) values (6, 1, 3)"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("UNIQUE");
    }

    @Test
    void lanesWithoutTheirHeatAndRepeatedLaneNumbersAreRemovedAndTheLaneKeysAdded() throws Exception {
        Path file = dir.resolve("orphans.sqlite");
        execute(file, "create table heat (id integer, changeSeq bigint not null, event integer not null, heatNumber integer not null, primary key (id))");
        execute(file, "create unique index heat_event_heatNumber on heat (event, heatNumber)");
        execute(file, "create table lane (id integer, number integer not null, timeInMillis integer not null, heat_id bigint, primary key (id))");
        execute(file, "insert into heat (id, changeSeq, event, heatNumber) values (7, 1, 1, 2)");
        execute(file, "insert into lane (id, number, timeInMillis, heat_id) values (1, 1, 100, 7), (2, 2, 200, 7), (3, 1, 300, 7), (4, 1, 400, 99), (5, 1, 500, null)");
        execute(file, "pragma user_version = 2");

        SchemaMigrations.migrate(file);

        assertThat(query(file, "select id, number, timeInMillis, heat_id from lane order by id")).containsExactly("1|1|100|7", "2|2|200|7");
        assertThat(query(file, "select \"table\", \"from\", \"to\" from pragma_foreign_key_list('lane')")).containsExactly("heat|heat_id|id");
        assertThat(query(file, "select \"notnull\" from pragma_table_info('lane') where name = 'heat_id'")).containsExactly("1");
        assertThatThrownBy(() -> executeWithForeignKeys(file, "insert into lane (number, timeInMillis, heat_id) values (3, 0, 99)"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("FOREIGN KEY");
        assertThatThrownBy(() -> execute(file, "insert into lane (number, timeInMillis, heat_id) values (2, 0, 7)"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("UNIQUE");
    }

    private static void executeWithForeignKeys(Path file, String sql) throws SQLException {
        try (Connection c = connect(file); Statement st = c.createStatement()) {
            st.execute("pragma foreign_keys = on");
            st.execute(sql);
        }
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
