package com.thegamecellar.libraryservice.integration;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.entity.UserGenrePreference;
import com.thegamecellar.libraryservice.model.entity.UserOnboarding;
import com.thegamecellar.libraryservice.model.entity.UserPlatform;
import com.thegamecellar.libraryservice.model.entity.UserReleaseYearPreference;
import com.thegamecellar.libraryservice.model.entity.UserTagPreference;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import com.thegamecellar.libraryservice.repository.UserGenrePreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserOnboardingRepository;
import com.thegamecellar.libraryservice.repository.UserPlatformRepository;
import com.thegamecellar.libraryservice.repository.UserReleaseYearPreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserTagPreferenceRepository;
import com.thegamecellar.libraryservice.service.GameServiceClient;
import com.thegamecellar.libraryservice.service.LibraryWritePublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The two GDPR endpoints driven the way the gateway calls them: an HTTP request with a bearer
// token through the real security chain, against Postgres running the real migrations. Only
// the decoder is mocked, so a token is whatever a test issues and anything else is rejected.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://unused"
})
class AccountEndpointsTest {

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
    private static final String EXPORT = "/api/v1/library/account/export";
    private static final String ACCOUNT = "/api/v1/library/account";

    @MockitoBean private GameServiceClient gameServiceClient;
    @MockitoBean private LibraryWritePublisher writePublisher;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserGameRepository games;
    @Autowired private UserPlatformRepository platforms;
    @Autowired private UserGenrePreferenceRepository genrePreferences;
    @Autowired private UserTagPreferenceRepository tagPreferences;
    @Autowired private UserReleaseYearPreferenceRepository releaseYearPreferences;
    @Autowired private UserOnboardingRepository onboarding;

    @BeforeEach
    void tokensAndALibraryForBothUsers() {
        lenient().when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("unknown token"));
        issue("alice-token", ALICE);
        issue("bob-token", BOB);
        seed(ALICE, 1001, "Hollow Knight", "Action");
        seed(BOB, 1002, "Stardew Valley", "Simulator");
    }

    @AfterEach
    void emptyEveryTable() {
        for (String table : List.of("user_game_platforms", "user_game_genres", "user_game_themes", "user_game_tags",
                "user_games", "user_platforms", "user_genre_preferences", "user_tag_preferences",
                "user_release_year_preferences", "user_onboarding", "account_deletions")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    // Keycloak-shaped, realm roles included, so the app's own role converter runs as in production.
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

    private void seed(String userId, int igdbId, String gameName, String genre) {
        games.save(UserGame.builder().userId(userId).igdbGameId(igdbId).gameName(gameName)
                .status(GameStatus.PLAYING).rating(new BigDecimal("8.5")).notes(gameName + " notes")
                .playtime(12).platforms(new ArrayList<>(List.of("PC"))).genres(new ArrayList<>(List.of(genre))).build());
        platforms.save(UserPlatform.builder().userId(userId).platformName("PC").isPrimary(true).build());
        genrePreferences.save(UserGenrePreference.builder().userId(userId).genreName(genre).build());
        tagPreferences.save(UserTagPreference.builder().userId(userId).tagName(genre + "-tag").build());
        releaseYearPreferences.save(UserReleaseYearPreference.builder().userId(userId).bucketLabel("2010s").build());
        onboarding.save(UserOnboarding.builder().userId(userId).build());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private int rows(String table, String userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    void exportWithoutATokenOrWithARejectedOneIsRefused() throws Exception {
        mvc.perform(get(EXPORT)).andExpect(status().isUnauthorized());
        mvc.perform(get(EXPORT).header(HttpHeaders.AUTHORIZATION, bearer("forged"))).andExpect(status().isUnauthorized());
    }

    // Every row the caller owns, with the timestamps and the onboarding date, and nothing of Bob's.
    @Test
    void exportHoldsEveryRowOfTheCallerAndNoneOfAnotherUsers() throws Exception {
        mvc.perform(get(EXPORT).header(HttpHeaders.AUTHORIZATION, bearer("alice-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ALICE))
                .andExpect(jsonPath("$.onboardingCompletedAt").value(notNullValue()))
                .andExpect(jsonPath("$.games", hasSize(1)))
                .andExpect(jsonPath("$.games[0].gameName").value("Hollow Knight"))
                .andExpect(jsonPath("$.games[0].notes").value("Hollow Knight notes"))
                .andExpect(jsonPath("$.games[0].rating").value(8.5))
                .andExpect(jsonPath("$.games[0].platforms[0]").value("PC"))
                .andExpect(jsonPath("$.games[0].dateAdded").value(notNullValue()))
                .andExpect(jsonPath("$.games[0].statusChangedAt").value(notNullValue()))
                .andExpect(jsonPath("$.games[0].createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.games[0].updatedAt").value(notNullValue()))
                .andExpect(jsonPath("$.platforms", hasSize(1)))
                .andExpect(jsonPath("$.genrePreferences[0].genreName").value("Action"))
                .andExpect(jsonPath("$.genrePreferences[0].createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.tagPreferences[0].tagName").value("Action-tag"))
                .andExpect(jsonPath("$.tagPreferences[0].createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.releaseYearPreferences[0].bucketLabel").value("2010s"))
                .andExpect(jsonPath("$.releaseYearPreferences[0].createdAt").value(notNullValue()))
                .andExpect(content().string(not(containsString("Stardew Valley"))))
                .andExpect(content().string(not(containsString(BOB))));
    }

    @Test
    void deleteWithoutATokenOrWithARejectedOneTouchesNothing() throws Exception {
        mvc.perform(delete(ACCOUNT)).andExpect(status().isUnauthorized());
        mvc.perform(delete(ACCOUNT).header(HttpHeaders.AUTHORIZATION, bearer("forged"))).andExpect(status().isUnauthorized());

        assertThat(rows("user_games", ALICE)).isEqualTo(1);
        assertThat(rows("user_onboarding", ALICE)).isEqualTo(1);
        assertThat(rows("account_deletions", ALICE)).isZero();
    }

    // The caller is taken from the token, so the purge reaches every table for Alice, writes her
    // ledger row, and leaves every one of Bob's rows where it was.
    @Test
    void deletePurgesEveryTableForTheCallerAndLeavesAnotherUserAlone() throws Exception {
        mvc.perform(delete(ACCOUNT).header(HttpHeaders.AUTHORIZATION, bearer("alice-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gamesRemoved").value(1))
                .andExpect(jsonPath("$.onboardingRemoved").value(1));

        for (String table : List.of("user_games", "user_platforms", "user_genre_preferences",
                "user_tag_preferences", "user_release_year_preferences", "user_onboarding")) {
            assertThat(rows(table, ALICE)).as(table + " for the caller").isZero();
            assertThat(rows(table, BOB)).as(table + " for another user").isEqualTo(1);
        }
        assertThat(rows("account_deletions", ALICE)).isEqualTo(1);
        assertThat(rows("account_deletions", BOB)).isZero();
    }
}
