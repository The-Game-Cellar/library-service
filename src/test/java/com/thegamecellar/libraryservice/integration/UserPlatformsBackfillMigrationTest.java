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
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// The backfill run the way production gets it: the real migrations up to the one before it, rows
// written in that schema, then the rest of the migrations on top. A database of its own, since the
// shared one is already migrated to the end.
@Testcontainers
class UserPlatformsBackfillMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("library_migration_db")
            .withUsername("test")
            .withPassword("test");

    private static final String ALICE = "11111111-0000-4000-8000-000000000001";
    private static final String BOB = "22222222-0000-4000-8000-000000000002";
    private static final String CAROL = "33333333-0000-4000-8000-000000000003";

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

    private void entry(String userId, int igdbId, String... platformNames) {
        Long id = jdbc.queryForObject("""
                INSERT INTO user_games (user_id, igdb_game_id, game_name, status, status_changed_at)
                VALUES (?, ?, ?, 'BACKLOG', CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, userId, igdbId, "Game " + igdbId);
        for (int position = 0; position < platformNames.length; position++) {
            jdbc.update("INSERT INTO user_game_platforms (user_game_id, platform, position) VALUES (?, ?, ?)",
                    id, platformNames[position], position);
        }
    }

    private void own(String userId, String platformName, boolean primary) {
        jdbc.update("INSERT INTO user_platforms (user_id, platform_name, is_primary) VALUES (?, ?, ?)",
                userId, platformName, primary);
    }

    // Name to primary flag, failing on a second row for the same name.
    private Map<String, Boolean> profile(String userId) {
        Map<String, Boolean> rows = new HashMap<>();
        jdbc.query("SELECT platform_name, is_primary FROM user_platforms WHERE user_id = ?",
                rs -> {
                    Boolean previous = rows.put(rs.getString("platform_name"), rs.getBoolean("is_primary"));
                    assertThat(previous).as("one row per platform").isNull();
                }, userId);
        return rows;
    }

    @Test
    void everyEntryPlatformJoinsItsOwnersProfileAsNonPrimaryAndNothingElseMoves() {
        flywayUpTo("13").migrate();
        own(ALICE, "PC", true);
        entry(ALICE, 1001, "PC", "PlayStation 5");
        entry(ALICE, 1002, "PlayStation 5", "Xbox 360");
        entry(BOB, 2001, "Nintendo Switch");
        own(CAROL, "PC", false);

        flywayUpTo("latest").migrate();

        assertThat(profile(ALICE)).isEqualTo(Map.of("PC", true, "PlayStation 5", false, "Xbox 360", false));
        assertThat(profile(BOB)).isEqualTo(Map.of("Nintendo Switch", false));
        assertThat(profile(CAROL)).isEqualTo(Map.of("PC", false));
    }
}
