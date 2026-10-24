package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.entity.UserGenrePreference;
import com.thegamecellar.libraryservice.model.entity.UserReleaseYearPreference;
import com.thegamecellar.libraryservice.model.entity.UserTagPreference;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.repository.UserGenrePreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserReleaseYearPreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserTagPreferenceRepository;
import com.thegamecellar.libraryservice.support.LibraryServiceTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The security chain permits /internal/**, so InternalAuthFilter and its shared token are the only guard.
// The user id is a path value here because the caller is rec-service's worker, which holds no user token.
class InternalEndpointsTest extends LibraryServiceTestBase {

    private static final String USERS = "/internal/library/users/";
    private static final String DELETIONS = "/internal/library/account-deletions";

    @Autowired private UserGenrePreferenceRepository genrePreferences;
    @Autowired private UserTagPreferenceRepository tagPreferences;
    @Autowired private UserReleaseYearPreferenceRepository releaseYearPreferences;

    @BeforeEach
    void twoUsers() {
        entry(ALICE, 1001, "Hollow Knight", GameStatus.BACKLOG, "PC");
        entry(BOB, 2002, "Stardew Valley", GameStatus.PLAYING, "Nintendo Switch");
        own(ALICE, "PC", true);
        own(BOB, "Nintendo Switch", true);
        genrePreferences.save(UserGenrePreference.builder().userId(ALICE).genreName("Shooter").build());
        genrePreferences.save(UserGenrePreference.builder().userId(BOB).genreName("Simulator").build());
        tagPreferences.save(UserTagPreference.builder().userId(ALICE).tagName("metroidvania").build());
        releaseYearPreferences.save(UserReleaseYearPreference.builder().userId(ALICE).bucketLabel("2010s").build());
    }

    private ResultActions internal(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(INTERNAL_HEADER, INTERNAL_TOKEN));
    }

    private static MockHttpServletRequestBuilder call(String endpoint) {
        String[] parts = endpoint.split(" ", 2);
        return "GET".equals(parts[0]) ? get(parts[1]) : post(parts[1]);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET " + USERS + ALICE + "/games", "GET " + USERS + ALICE + "/platforms",
            "GET " + USERS + ALICE + "/preferences/genres", "GET " + USERS + ALICE + "/preferences/tags",
            "GET " + USERS + ALICE + "/preferences/release-years", "GET " + DELETIONS + "/pending",
            "POST " + DELETIONS + "/" + ALICE + "/complete"})
    void withoutTheSharedTokenOrWithAWrongOneEveryInternalEndpointAnswers401(String endpoint) throws Exception {
        mvc.perform(call(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(call(endpoint).header(INTERNAL_HEADER, "wrong-internal-token")).andExpect(status().isUnauthorized());
        mvc.perform(call(endpoint).header(INTERNAL_HEADER, INTERNAL_TOKEN + "x")).andExpect(status().isUnauthorized());
        // A user's token opens the public API, never the internal one.
        mvc.perform(call(endpoint).header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))).andExpect(status().isUnauthorized());
    }

    @Test
    void theWorkerReadsOneUsersLibraryWithoutCallingGameService() throws Exception {
        internal(get(USERS + ALICE + "/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].igdbGameId", contains(1001)));
        internal(get(USERS + BOB + "/games"))
                .andExpect(jsonPath("$[*].igdbGameId", contains(2002)));
        assertThat(GAME_SERVICE.recorded()).isEmpty();
    }

    @Test
    void theWorkerReadsOneUsersPlatformsAndPreferences() throws Exception {
        internal(get(USERS + ALICE + "/platforms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].platformName", contains("PC")));
        internal(get(USERS + ALICE + "/preferences/genres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].genreName", contains("Shooter")));
        internal(get(USERS + ALICE + "/preferences/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tagName", contains("metroidvania")));
        internal(get(USERS + ALICE + "/preferences/release-years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].bucketLabel", contains("2010s")));
        internal(get(USERS + BOB + "/preferences/tags"))
                .andExpect(jsonPath("$", empty()));
    }

    // A request becomes visible to the gateway's retry job only after the grace window, and completing
    // it twice is the same as once.
    @Test
    void pendingDeletionsSurfaceAfterTheGraceAndCompletingIsIdempotent() throws Exception {
        jdbc.update("INSERT INTO account_deletions (user_id, requested_at) VALUES (?, ?), (?, ?)",
                ALICE, Timestamp.valueOf(LocalDateTime.now().minusMinutes(5)),
                BOB, Timestamp.valueOf(LocalDateTime.now()));

        internal(get(DELETIONS + "/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].userId", contains(ALICE)));

        internal(post(DELETIONS + "/" + ALICE + "/complete")).andExpect(status().isNoContent());
        internal(post(DELETIONS + "/" + ALICE + "/complete")).andExpect(status().isNoContent());
        internal(post(DELETIONS + "/unknown-user/complete")).andExpect(status().isNoContent());

        internal(get(DELETIONS + "/pending")).andExpect(jsonPath("$", empty()));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_deletions WHERE identity_deleted_at IS NOT NULL", Integer.class)).isEqualTo(1);
    }
}
