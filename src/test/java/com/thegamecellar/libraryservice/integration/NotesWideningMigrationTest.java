package com.thegamecellar.libraryservice.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

// V15 run the way production gets it: a note written under VARCHAR(255), then the widening on top.
// A database of its own, since the shared one is already migrated to the end.
@Testcontainers
class NotesWideningMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("library_migration_db")
            .withUsername("test")
            .withPassword("test");

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void connect() {
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    private Flyway flywayUpTo(String target) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
    }

    @Test
    void theNoteColumnBecomesTextAndKeepsTheNotesAlreadyWritten() {
        flywayUpTo("14").migrate();
        String existing = "e".repeat(255);
        jdbc.update("""
                INSERT INTO user_games (user_id, igdb_game_id, game_name, status, notes, status_changed_at)
                VALUES ('11111111-0000-4000-8000-000000000001', 1001, 'Hollow Knight', 'BACKLOG', ?, CURRENT_TIMESTAMP)
                """, existing);

        flywayUpTo("latest").migrate();

        assertThat(jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name = 'user_games' AND column_name = 'notes'",
                String.class)).isEqualTo("text");
        assertThat(jdbc.queryForObject("SELECT notes FROM user_games WHERE igdb_game_id = 1001", String.class))
                .isEqualTo(existing);
        jdbc.update("UPDATE user_games SET notes = ? WHERE igdb_game_id = 1001", "n".repeat(10_000));
        assertThat(jdbc.queryForObject("SELECT LENGTH(notes) FROM user_games WHERE igdb_game_id = 1001", Integer.class))
                .isEqualTo(10_000);
    }
}
