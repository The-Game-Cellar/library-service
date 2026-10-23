package com.thegamecellar.libraryservice.integration;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.entity.UserPlatform;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import com.thegamecellar.libraryservice.repository.UserPlatformRepository;
import com.thegamecellar.libraryservice.service.GameServiceClient;
import com.thegamecellar.libraryservice.service.LibraryWritePublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A library entry's platforms join the user's own platforms, driven the way the gateway calls the
// two writes: an HTTP request with a bearer token through the real security chain, against
// Postgres running the real migrations.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://unused"
})
class EntryPlatformsJoinProfileTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("library_test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String ALICE = "11111111-0000-4000-8000-000000000001";
    private static final String BOB = "22222222-0000-4000-8000-000000000002";
    private static final String GAMES = "/api/v1/library/games";

    @MockitoBean private GameServiceClient gameServiceClient;
    @MockitoBean private LibraryWritePublisher writePublisher;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserGameRepository games;
    @Autowired private UserPlatformRepository platforms;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void tokensACatalogAndAProfileForBothUsers() {
        lenient().when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("unknown token"));
        issue("alice-token", ALICE);
        issue("bob-token", BOB);
        when(gameServiceClient.getGameInfo(anyInt(), any())).thenReturn(new GameServiceClient.GameInfo(
                "Hollow Knight", "hk.jpg", List.of("Platform"), List.of(), List.of(), "2017-02-24"));
        own(ALICE, "PC", true);
        own(BOB, "Nintendo Switch", false);
    }

    @AfterEach
    void emptyEveryTable() {
        for (String table : List.of("user_game_platforms", "user_game_genres", "user_game_themes", "user_game_tags",
                "user_games", "user_platforms")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    private void issue(String token, String subject) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .build();
        doReturn(jwt).when(jwtDecoder).decode(token);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private void own(String userId, String platformName, boolean primary) {
        platforms.save(UserPlatform.builder().userId(userId).platformName(platformName).isPrimary(primary).build());
    }

    private long entry(String userId, int igdbId, String... platformNames) {
        return games.save(UserGame.builder().userId(userId).igdbGameId(igdbId).gameName("Seeded " + igdbId)
                .status(GameStatus.BACKLOG).platforms(new ArrayList<>(List.of(platformNames))).build()).getId();
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
    void addingAGameAddsItsMissingPlatformsToTheCallerAndLeavesTheOnesThereAlone() throws Exception {
        mvc.perform(post(GAMES).header(HttpHeaders.AUTHORIZATION, bearer("alice-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"igdbGameId": 1001, "gameName": "Hollow Knight", "status": "BACKLOG",
                                 "platforms": ["PlayStation 5", " PC "]}
                                """))
                .andExpect(status().isCreated());

        assertThat(profile(ALICE)).isEqualTo(Map.of("PC", true, "PlayStation 5", false));
        assertThat(profile(BOB)).isEqualTo(Map.of("Nintendo Switch", false));
    }

    // Only the platforms the entry gains are added; one it loses stays in the profile.
    @Test
    void changingAnEntrysPlatformsAddsTheNewOnesAndRemovesNone() throws Exception {
        long id = entry(ALICE, 1001, "PC", "PlayStation 5");
        own(ALICE, "PlayStation 5", false);

        mvc.perform(put(GAMES + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer("alice-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"platforms": ["Nintendo Switch"]}
                                """))
                .andExpect(status().isOk());

        assertThat(profile(ALICE)).isEqualTo(Map.of("Nintendo Switch", false, "PC", true, "PlayStation 5", false));
        assertThat(profile(BOB)).isEqualTo(Map.of("Nintendo Switch", false));
    }

    // The single `platform` field the v1 client sends takes the same path as the list.
    @Test
    void theSinglePlatformFieldJoinsTheProfileToo() throws Exception {
        long id = entry(ALICE, 1001, "PC");

        mvc.perform(put(GAMES + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer("alice-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"platform": "Xbox 360"}
                                """))
                .andExpect(status().isOk());

        assertThat(profile(ALICE)).isEqualTo(Map.of("PC", true, "Xbox 360", false));
    }

    // An update that does not name platforms is not a platform change, so an entry already
    // outside the profile stays outside it.
    @Test
    void anUpdateThatLeavesThePlatformsAloneAddsNothing() throws Exception {
        long id = entry(ALICE, 1001, "Xbox 360");

        mvc.perform(put(GAMES + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer("alice-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PLAYING", "rating": 8}
                                """))
                .andExpect(status().isOk());

        assertThat(profile(ALICE)).isEqualTo(Map.of("PC", true));
    }

    @Test
    void aRefusedWriteAddsNoPlatformForAnyone() throws Exception {
        long bobsEntry = entry(BOB, 2002, "Nintendo Switch");
        entry(ALICE, 1001, "PC");
        String addOnPs5 = """
                {"igdbGameId": 1001, "gameName": "Hollow Knight", "status": "BACKLOG", "platforms": ["PlayStation 5"]}
                """;
        String moveToPs5 = """
                {"platforms": ["PlayStation 5"]}
                """;

        mvc.perform(post(GAMES).contentType(MediaType.APPLICATION_JSON).content(addOnPs5))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(GAMES).header(HttpHeaders.AUTHORIZATION, bearer("forged"))
                        .contentType(MediaType.APPLICATION_JSON).content(addOnPs5))
                .andExpect(status().isUnauthorized());
        // Already in Alice's library
        mvc.perform(post(GAMES).header(HttpHeaders.AUTHORIZATION, bearer("alice-token"))
                        .contentType(MediaType.APPLICATION_JSON).content(addOnPs5))
                .andExpect(status().isConflict());
        // Bob's entry, answered as if it did not exist
        mvc.perform(put(GAMES + "/" + bobsEntry).header(HttpHeaders.AUTHORIZATION, bearer("alice-token"))
                        .contentType(MediaType.APPLICATION_JSON).content(moveToPs5))
                .andExpect(status().isNotFound());
        mvc.perform(put(GAMES + "/" + bobsEntry).contentType(MediaType.APPLICATION_JSON).content(moveToPs5))
                .andExpect(status().isUnauthorized());

        assertThat(profile(ALICE)).isEqualTo(Map.of("PC", true));
        assertThat(profile(BOB)).isEqualTo(Map.of("Nintendo Switch", false));
    }

    // Two transactions adding the same platform at once: the second waits on the first's row and
    // then does nothing, where a plain insert would fail on the unique constraint and roll back
    // the entry it was written with.
    @Test
    void insertIfAbsentLetsTwoConcurrentWritesOfTheSamePlatformBothCommit() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch firstHoldsTheRow = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> tx.execute(s -> {
                int added = platforms.insertIfAbsent(ALICE, "PlayStation 5");
                firstHoldsTheRow.countDown();
                awaitQuietly(release);
                return added;
            }));
            assertThat(firstHoldsTheRow.await(10, SECONDS)).isTrue();
            Future<Integer> second = pool.submit(() -> tx.execute(s -> platforms.insertIfAbsent(ALICE, "PlayStation 5")));
            awaitABackendBlockedOnALock();
            release.countDown();

            assertThat(first.get(10, SECONDS)).isEqualTo(1);
            assertThat(second.get(10, SECONDS)).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(profile(ALICE)).isEqualTo(Map.of("PC", true, "PlayStation 5", false));
    }

    private void awaitABackendBlockedOnALock() throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND datname = current_database()",
                    Integer.class);
            if (waiting != null && waiting > 0) return;
            Thread.sleep(20);
        }
        throw new AssertionError("the second insert never waited on the first one's row");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
