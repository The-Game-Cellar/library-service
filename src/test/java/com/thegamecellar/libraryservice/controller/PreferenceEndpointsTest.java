package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.support.LibraryServiceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The three declared preference lists and the onboarding mark. A preference write replaces the
// whole list and tells rec-service on the library-write channel.
class PreferenceEndpointsTest extends LibraryServiceTestBase {

    private static final String LIBRARY = "/api/v1/library";

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void genrePreferencesAreReplacedTrimmedAndDedupedAndPublishTheCaller() throws Exception {
        as(BOB_TOKEN, json(put(LIBRARY + "/genre-preferences"), "{\"genres\": [\"Simulator\"]}")).andExpect(status().isOk());
        as(ALICE_TOKEN, json(put(LIBRARY + "/genre-preferences"), "{\"genres\": [\"Shooter\", \"Puzzle\"]}")).andExpect(status().isOk());
        libraryWrites();

        as(ALICE_TOKEN, json(put(LIBRARY + "/genre-preferences"), "{\"genres\": [\"Puzzle\", \" Strategy \", \"Strategy\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].genreName", containsInAnyOrder("Puzzle", "Strategy")));

        as(ALICE_TOKEN, get(LIBRARY + "/genre-preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].genreName", containsInAnyOrder("Puzzle", "Strategy")));
        as(BOB_TOKEN, get(LIBRARY + "/genre-preferences"))
                .andExpect(jsonPath("$[*].genreName", containsInAnyOrder("Simulator")));
        assertThat(libraryWrites()).containsExactly(ALICE);
    }

    @Test
    void tagPreferencesAreReplacedAndAnEmptyListClearsThem() throws Exception {
        as(ALICE_TOKEN, json(put(LIBRARY + "/tag-preferences"), "{\"tags\": [\"cozy\", \"roguelike\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tagName", containsInAnyOrder("cozy", "roguelike")));
        as(ALICE_TOKEN, json(put(LIBRARY + "/tag-preferences"), "{\"tags\": []}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));

        as(ALICE_TOKEN, get(LIBRARY + "/tag-preferences")).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
        assertThat(libraryWrites()).containsExactly(ALICE, ALICE);
    }

    // Labels outside the five buckets are dropped rather than refused, so a scheme change cannot break saved rows.
    @Test
    void releaseYearPreferencesKeepOnlyTheKnownBuckets() throws Exception {
        as(ALICE_TOKEN, json(put(LIBRARY + "/release-year-preferences"), "{\"buckets\": [\"1990s\", \"1980s\", \"2020s\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].bucketLabel", containsInAnyOrder("1990s", "2020s")));

        as(ALICE_TOKEN, get(LIBRARY + "/release-year-preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].bucketLabel", containsInAnyOrder("1990s", "2020s")));
        assertThat(libraryWrites()).containsExactly(ALICE);
    }

    // Marking onboarding twice keeps the first timestamp.
    @Test
    void onboardingIsUnmarkedUntilPostedAndASecondPostKeepsTheFirstTimestamp() throws Exception {
        as(ALICE_TOKEN, get(LIBRARY + "/onboarding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedAt").value(nullValue()));

        as(ALICE_TOKEN, post(LIBRARY + "/onboarding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedAt").value(notNullValue()));
        String first = jdbc.queryForObject("SELECT completed_at::text FROM user_onboarding WHERE user_id = ?", String.class, ALICE);

        as(ALICE_TOKEN, post(LIBRARY + "/onboarding")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT completed_at::text FROM user_onboarding WHERE user_id = ?", String.class, ALICE))
                .isEqualTo(first);
        as(BOB_TOKEN, get(LIBRARY + "/onboarding")).andExpect(jsonPath("$.completed").value(false));
    }
}
