package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.support.LibraryServiceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A refused preference write is refused before the service runs, so nothing is stored or published.
class PreferenceValidationTest extends LibraryServiceTestBase {

    @Test
    void genrePreferences_withEmptyStringElement_returns400() throws Exception {
        mvc.perform(put("/api/v1/library/genre-preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genres\":[\"\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(greaterThan(0)));
        assertThat(libraryWrites()).isEmpty();
    }

    @Test
    void genrePreferences_withNullList_returns400() throws Exception {
        mvc.perform(put("/api/v1/library/genre-preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genres\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void tagPreferences_withElementOverMaxSize_returns400() throws Exception {
        String tooLong = "x".repeat(101);
        mvc.perform(put("/api/v1/library/tag-preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"" + tooLong + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.length()").value(greaterThan(0)));
    }

    @Test
    void releaseYearPreferences_withEmptyStringElement_returns400() throws Exception {
        mvc.perform(put("/api/v1/library/release-year-preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buckets\":[\"\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.length()").value(greaterThan(0)));
    }

    @Test
    void releaseYearPreferences_withElementOverMaxSize_returns400() throws Exception {
        mvc.perform(put("/api/v1/library/release-year-preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buckets\":[\"" + "y".repeat(51) + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(greaterThan(0)));
    }

    @Test
    void everyPreferenceListIsRequired() throws Exception {
        for (String path : new String[]{"genre-preferences", "tag-preferences", "release-year-preferences"}) {
            mvc.perform(put("/api/v1/library/" + path)
                            .header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM user_genre_preferences) + (SELECT COUNT(*) FROM user_tag_preferences)
                     + (SELECT COUNT(*) FROM user_release_year_preferences)
                """, Integer.class)).isZero();
    }
}
