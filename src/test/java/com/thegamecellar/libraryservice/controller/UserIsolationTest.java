package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.entity.UserGenrePreference;
import com.thegamecellar.libraryservice.model.entity.UserOnboarding;
import com.thegamecellar.libraryservice.model.entity.UserReleaseYearPreference;
import com.thegamecellar.libraryservice.model.entity.UserTagPreference;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.repository.UserGenrePreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserOnboardingRepository;
import com.thegamecellar.libraryservice.repository.UserReleaseYearPreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserTagPreferenceRepository;
import com.thegamecellar.libraryservice.support.LibraryServiceTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Every user-keyed endpoint acts on the user in the token: without one it refuses, and with Alice's it
// reads nothing of Bob's and changes nothing of Bob's, whichever of Bob's ids it is handed.
class UserIsolationTest extends LibraryServiceTestBase {

    private static final String LIBRARY = "/api/v1/library";
    private static final List<String> BOBS_MARKERS =
            List.of("Stardew", "Nintendo Switch", "Simulator", "Strategy", "farming", "1990s");

    @Autowired private UserGenrePreferenceRepository genrePreferences;
    @Autowired private UserTagPreferenceRepository tagPreferences;
    @Autowired private UserReleaseYearPreferenceRepository releaseYearPreferences;
    @Autowired private UserOnboardingRepository onboarding;

    private long alicesGame;
    private long bobsGame;
    private long bobsPlatform;

    @BeforeEach
    void twoFullAccounts() {
        alicesGame = seed(ALICE, 1001, "Hollow Knight", "Platform", "PC", "Shooter", "metroidvania", "2010s").getId();
        bobsGame = seed(BOB, 2002, "Stardew Valley", "Simulator", "Nintendo Switch", "Strategy", "farming", "1990s").getId();
        bobsPlatform = platforms.findByUserId(BOB).get(0).getId();
        onboarding.save(UserOnboarding.builder().userId(BOB).build());
    }

    private UserGame seed(String userId, int igdbId, String name, String genre, String platform,
                          String genrePreference, String tagPreference, String yearBucket) {
        UserGame game = games.save(UserGame.builder().userId(userId).igdbGameId(igdbId).gameName(name)
                .status(GameStatus.BACKLOG).rating(new BigDecimal("8.0")).notes(name + " notes")
                .genres(new ArrayList<>(List.of(genre))).platforms(new ArrayList<>(List.of(platform)))
                .metadataSyncedAt(LocalDateTime.now()).build());
        own(userId, platform, true);
        genrePreferences.save(UserGenrePreference.builder().userId(userId).genreName(genrePreference).build());
        tagPreferences.save(UserTagPreference.builder().userId(userId).tagName(tagPreference).build());
        releaseYearPreferences.save(UserReleaseYearPreference.builder().userId(userId).bucketLabel(yearBucket).build());
        return game;
    }

    // Everything of Bob's in one string, so a test can prove none of it moved.
    private String bobsRows() {
        return String.join("|", jdbc.queryForList("""
                SELECT 'game:' || igdb_game_id || ':' || status || ':' || rating || ':' || notes FROM user_games WHERE user_id = ?1
                UNION ALL SELECT 'entry-platform:' || p.platform FROM user_game_platforms p
                    JOIN user_games g ON g.id = p.user_game_id WHERE g.user_id = ?1
                UNION ALL SELECT 'platform:' || platform_name || ':' || is_primary FROM user_platforms WHERE user_id = ?1
                UNION ALL SELECT 'genre:' || genre_name FROM user_genre_preferences WHERE user_id = ?1
                UNION ALL SELECT 'tag:' || tag_name FROM user_tag_preferences WHERE user_id = ?1
                UNION ALL SELECT 'year:' || bucket_label FROM user_release_year_preferences WHERE user_id = ?1
                UNION ALL SELECT 'onboarded:' || completed_at FROM user_onboarding WHERE user_id = ?1
                UNION ALL SELECT 'deletion:' || user_id FROM account_deletions WHERE user_id = ?1
                ORDER BY 1
                """.replace("?1", "'" + BOB + "'"), String.class));
    }

    private static MockHttpServletRequestBuilder call(String endpoint) {
        String[] parts = endpoint.split(" ", 3);
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(parts[0]), LIBRARY + parts[1]);
        return parts.length == 3 ? builder.contentType(MediaType.APPLICATION_JSON).content(parts[2]) : builder;
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET /games", "GET /genres", "GET /games/platforms", "GET /igdb-ids", "GET /games/1",
            "GET /games/by-igdb/1001", "POST /games {\"igdbGameId\":3,\"gameName\":\"X\",\"status\":\"BACKLOG\",\"platform\":\"PC\"}",
            "PUT /games/1 {\"status\":\"PLAYING\"}", "DELETE /games/1", "GET /backlog", "GET /wishlist", "GET /playing",
            "GET /completed", "GET /stats", "GET /dusty", "GET /platforms",
            "POST /platforms {\"platformName\":\"Wii\",\"isPrimary\":false}", "DELETE /platforms/1",
            "PATCH /platforms/1/primary {\"isPrimary\":false}", "GET /genre-preferences",
            "PUT /genre-preferences {\"genres\":[\"X\"]}", "GET /tag-preferences", "PUT /tag-preferences {\"tags\":[\"x\"]}",
            "GET /release-year-preferences", "PUT /release-year-preferences {\"buckets\":[\"2020s\"]}",
            "GET /onboarding", "POST /onboarding", "GET /account/export", "DELETE /account"})
    void withoutATokenOrWithARejectedOneEveryUserEndpointRefusesAndTouchesNothing(String endpoint) throws Exception {
        String before = bobsRows();
        int alicesRows = games.findByUserId(ALICE).size();

        mvc.perform(call(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(call(endpoint).header(HttpHeaders.AUTHORIZATION, bearer("forged"))).andExpect(status().isUnauthorized());

        assertThat(bobsRows()).isEqualTo(before);
        assertThat(games.findByUserId(ALICE)).hasSize(alicesRows);
        assertThat(libraryWrites()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/games", "/genres", "/games/platforms", "/igdb-ids", "/backlog", "/wishlist", "/playing",
            "/completed", "/stats", "/dusty", "/platforms", "/genre-preferences", "/tag-preferences",
            "/release-year-preferences", "/onboarding", "/account/export"})
    void everyReadAsAliceHoldsNothingOfBobs(String path) throws Exception {
        String body = mvc.perform(call("GET " + path).header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(BOBS_MARKERS).doesNotContain(BOB);
    }

    @Test
    void bobsRowsByIdAreNotFoundForAliceAndOnboardingIsHers() throws Exception {
        mvc.perform(call("GET /games/" + bobsGame).header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN)))
                .andExpect(status().isNotFound());
        mvc.perform(call("GET /games/by-igdb/2002").header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN)))
                .andExpect(status().isNotFound());
        mvc.perform(call("GET /onboarding").header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN)))
                .andExpect(jsonPath("$.completed").value(false));
    }

    // Handed Bob's ids, Alice's writes answer 404, the answer an id that does not exist gets.
    @Test
    void everyIdKeyedWriteOnBobsRowsIsNotFoundAndLeavesThemAlone() throws Exception {
        String before = bobsRows();

        for (String endpoint : List.of(
                "PUT /games/" + bobsGame + " {\"status\":\"COMPLETED\",\"rating\":2,\"notes\":\"mine now\"}",
                "DELETE /games/" + bobsGame,
                "DELETE /platforms/" + bobsPlatform,
                "PATCH /platforms/" + bobsPlatform + "/primary {\"isPrimary\":false}")) {
            mvc.perform(call(endpoint).header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN)))
                    .andExpect(status().isNotFound());
        }

        assertThat(bobsRows()).isEqualTo(before);
        assertThat(libraryWrites()).isEmpty();
    }

    // Alice adding the game Bob owns, replacing her preferences, marking onboarding, taking the platform
    // name Bob has and removing her own entry: every one of these lands on Alice only.
    @Test
    void alicesOwnWritesLeaveEveryRowOfBobsAlone() throws Exception {
        catalogGame(2002, "Stardew Valley", List.of("Simulator"), List.of(), List.of(), "2016-02-26");
        String before = bobsRows();

        for (String endpoint : List.of(
                "POST /games {\"igdbGameId\":2002,\"gameName\":\"Stardew Valley\",\"status\":\"WISHLIST\",\"platform\":\"PC\"}",
                "PUT /genre-preferences {\"genres\":[\"Strategy\"]}",
                "PUT /tag-preferences {\"tags\":[\"farming\"]}",
                "PUT /release-year-preferences {\"buckets\":[\"1990s\"]}",
                "POST /onboarding",
                "POST /platforms {\"platformName\":\"Nintendo Switch\",\"isPrimary\":false}",
                "PUT /games/" + alicesGame + " {\"status\":\"PLAYING\"}",
                "DELETE /games/" + alicesGame)) {
            mvc.perform(call(endpoint).header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN)))
                    .andExpect(status().is2xxSuccessful());
        }

        assertThat(bobsRows()).isEqualTo(before);
        assertThat(libraryWrites()).containsOnly(ALICE).hasSize(6);
    }
}
