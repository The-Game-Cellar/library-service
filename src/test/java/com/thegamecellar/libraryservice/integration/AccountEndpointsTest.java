package com.thegamecellar.libraryservice.integration;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.entity.UserGenrePreference;
import com.thegamecellar.libraryservice.model.entity.UserOnboarding;
import com.thegamecellar.libraryservice.model.entity.UserPlatform;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The two GDPR endpoints driven the way the gateway calls them: an HTTP request with a bearer
// token through the real security chain, against Postgres running the real migrations. Only
// the decoder is mocked, so a token is whatever a test issues and anything else is rejected.
class AccountEndpointsTest extends LibraryServiceTestBase {

    private static final String EXPORT = "/api/v1/library/account/export";
    private static final String ACCOUNT = "/api/v1/library/account";

    @Autowired private UserGenrePreferenceRepository genrePreferences;
    @Autowired private UserTagPreferenceRepository tagPreferences;
    @Autowired private UserReleaseYearPreferenceRepository releaseYearPreferences;
    @Autowired private UserOnboardingRepository onboarding;

    @BeforeEach
    void aLibraryForBothUsers() {
        seed(ALICE, 1001, "Hollow Knight", "Action");
        seed(BOB, 1002, "Stardew Valley", "Simulator");
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
