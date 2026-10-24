package com.thegamecellar.libraryservice.support;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.entity.UserPlatform;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import com.thegamecellar.libraryservice.repository.UserPlatformRepository;
import com.thegamecellar.libraryservice.service.LibraryWritePublisher;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;

// One Spring context, one Postgres and one Redis for the whole suite: the real migrations build the
// schema, Hibernate validates against it, and the write signal goes out on a real Redis channel. Only
// what lies outside the service is replaced: the JwtDecoder (a token is whatever a test issues) and
// game-service, a stub at its URL so library-service's own HTTP clients do the talking.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class LibraryServiceTestBase {

    // Started once and never stopped: every test class shares them, and Testcontainers removes them at JVM exit.
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("library_test_db")
            .withUsername("test")
            .withPassword("test");
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    protected static final StubHttpServer GAME_SERVICE = StubHttpServer.start();

    // What rec-service's subscriber would receive on the library-write channel.
    private static final BlockingQueue<String> LIBRARY_WRITES = new LinkedBlockingQueue<>();
    private static final StatefulRedisConnection<String, String> SENTINEL_PUBLISHER;

    static {
        POSTGRES.start();
        REDIS.start();
        RedisClient client = RedisClient.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        StatefulRedisPubSubConnection<String, String> subscriber = client.connectPubSub();
        subscriber.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                LIBRARY_WRITES.add(message);
            }
        });
        subscriber.sync().subscribe(LibraryWritePublisher.CHANNEL);
        SENTINEL_PUBLISHER = client.connect();
    }

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("game-service.url", GAME_SERVICE::baseUrl);
    }

    protected static final String ALICE = "11111111-0000-4000-8000-000000000001";
    protected static final String BOB = "22222222-0000-4000-8000-000000000002";
    protected static final String ADMIN = "aaaaaaaa-0000-4000-8000-00000000000a";
    protected static final String ALICE_TOKEN = "alice-token";
    protected static final String BOB_TOKEN = "bob-token";
    protected static final String ADMIN_TOKEN = "admin-token";
    protected static final String INTERNAL_HEADER = "X-Internal-Token";
    protected static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserGameRepository games;
    @Autowired protected UserPlatformRepository platforms;

    @MockitoBean protected JwtDecoder jwtDecoder;

    @BeforeEach
    void tokensStubAndChannel() {
        lenient().when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("unknown token"));
        issue(ALICE_TOKEN, ALICE, "USER");
        issue(BOB_TOKEN, BOB, "USER");
        issue(ADMIN_TOKEN, ADMIN, "USER", "ADMIN");
        GAME_SERVICE.reset();
        LIBRARY_WRITES.clear();
    }

    @AfterEach
    void emptyEveryTable() {
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
                String.class);
        jdbc.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
    }

    // Keycloak-shaped, realm roles included, so the app's own role converter runs as in production.
    protected void issue(String token, String subject, String... realmRoles) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("realm_access", Map.of("roles", List.of(realmRoles)))
                .build();
        doReturn(jwt).when(jwtDecoder).decode(token);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    // Every library-write message since the test began. A sentinel published after them marks the end:
    // Redis delivers one channel's messages in order, so anything the service sent arrives before it.
    protected static List<String> libraryWrites() throws InterruptedException {
        String sentinel = "sentinel-" + UUID.randomUUID();
        SENTINEL_PUBLISHER.sync().publish(LibraryWritePublisher.CHANNEL, sentinel);
        List<String> seen = new ArrayList<>();
        while (true) {
            String message = LIBRARY_WRITES.poll(5, TimeUnit.SECONDS);
            if (message == null) fail("The sentinel never came back on " + LibraryWritePublisher.CHANNEL);
            if (message.equals(sentinel)) return seen;
            seen.add(message);
        }
    }

    // ── game-service stub ─────────────────────────────────────────────────────

    protected static void catalogGame(int igdbId, String name, List<String> genres, List<String> themes,
                                      List<String> tags, String released) {
        GAME_SERVICE.on("GET", "/api/v1/games/" + igdbId, StubHttpServer.StubResponse.json(200,
                "{\"igdbId\":%d,\"name\":\"%s\",\"backgroundImage\":\"%s.jpg\",\"genres\":%s,\"themes\":%s,\"tags\":%s,\"released\":\"%s\"}"
                        .formatted(igdbId, name, igdbId, json(genres), json(themes), json(tags), released)));
    }

    private static String json(List<String> values) {
        return values.stream().map(v -> "\"" + v + "\"").reduce((a, b) -> a + "," + b)
                .map(s -> "[" + s + "]").orElse("[]");
    }

    // ── seeding ───────────────────────────────────────────────────────────────

    protected UserGame entry(String userId, int igdbId, String name, GameStatus status, String... platformNames) {
        return games.save(UserGame.builder().userId(userId).igdbGameId(igdbId).gameName(name).status(status)
                .platforms(new ArrayList<>(List.of(platformNames))).build());
    }

    protected UserPlatform own(String userId, String platformName, boolean primary) {
        return platforms.save(UserPlatform.builder().userId(userId).platformName(platformName).isPrimary(primary).build());
    }
}
