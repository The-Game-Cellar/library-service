package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.support.LibraryServiceTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The user's own platforms, Alice acting with Bob's platforms beside hers.
class PlatformEndpointsTest extends LibraryServiceTestBase {

    private static final String PLATFORMS = "/api/v1/library/platforms";

    private long alicesPc;
    private long bobsSwitch;

    @BeforeEach
    void twoProfiles() {
        alicesPc = own(ALICE, "PC", true).getId();
        own(ALICE, "PlayStation 5", false);
        bobsSwitch = own(BOB, "Nintendo Switch", true).getId();
    }

    private ResultActions asAlice(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN)));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void theListHoldsTheCallersPlatformsOnly() throws Exception {
        asAlice(get(PLATFORMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].platformName", containsInAnyOrder("PC", "PlayStation 5")))
                .andExpect(jsonPath("$[?(@.platformName == 'PC')].isPrimary", contains(true)));
    }

    // Owning a platform is per user, so Alice can add the name Bob already owns.
    @Test
    void addingAPlatformAnswers201AndAnotherUsersSameNameIsNoConflict() throws Exception {
        asAlice(json(post(PLATFORMS), "{\"platformName\": \"Nintendo Switch\", \"isPrimary\": false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.platformName").value("Nintendo Switch"))
                .andExpect(jsonPath("$.isPrimary").value(false));

        assertThat(platforms.findByUserId(ALICE)).hasSize(3);
        assertThat(platforms.findByUserId(BOB)).hasSize(1);
    }

    @Test
    void addingAPlatformTheCallerOwnsIs409() throws Exception {
        asAlice(json(post(PLATFORMS), "{\"platformName\": \"PC\", \"isPrimary\": false}"))
                .andExpect(status().isConflict());
        assertThat(platforms.findByUserId(ALICE)).hasSize(2);
    }

    @Test
    void aPlatformNameLongerThanItsColumnIs400() throws Exception {
        asAlice(json(post(PLATFORMS), "{\"platformName\": \"%s\", \"isPrimary\": false}".formatted("P".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].path").value("platformName"));
        asAlice(json(post(PLATFORMS), "{\"platformName\": \"%s\", \"isPrimary\": false}".formatted("P".repeat(255))))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"platformName\": \" \", \"isPrimary\": false}", "{\"platformName\": \"Wii\"}",
            "{\"isPrimary\": true}", "{\"platformName\": \"Wii\", \"isPrimary\": \"maybe\"}", "{", ""})
    void anAddWithAMissingOrMalformedFieldIs400(String body) throws Exception {
        asAlice(json(post(PLATFORMS), body)).andExpect(status().isBadRequest());
        assertThat(platforms.findByUserId(ALICE)).hasSize(2);
    }

    @Test
    void removingAPlatformAnswers204() throws Exception {
        asAlice(delete(PLATFORMS + "/" + alicesPc)).andExpect(status().isNoContent());
        assertThat(platforms.findById(alicesPc)).isEmpty();
    }

    @Test
    void settingPrimaryFlipsTheFlagOnTheCallersPlatform() throws Exception {
        asAlice(json(patch(PLATFORMS + "/" + alicesPc + "/primary"), "{\"isPrimary\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrimary").value(false));
        assertThat(platforms.findById(alicesPc).orElseThrow().getIsPrimary()).isFalse();
    }

    // Bob's platform is answered exactly as one that does not exist, and stays as it was.
    @Test
    void anotherUsersPlatformIsNotFoundForRemovalOrPrimary() throws Exception {
        asAlice(delete(PLATFORMS + "/" + bobsSwitch))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Platform not found: " + bobsSwitch));
        asAlice(json(patch(PLATFORMS + "/" + bobsSwitch + "/primary"), "{\"isPrimary\": false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Platform not found: " + bobsSwitch));
        asAlice(delete(PLATFORMS + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Platform not found: 999999"));

        assertThat(platforms.findById(bobsSwitch).orElseThrow().getIsPrimary()).isTrue();
    }

    @Test
    void aBadIdOrAMissingFlagIs400() throws Exception {
        asAlice(delete(PLATFORMS + "/0")).andExpect(status().isBadRequest());
        asAlice(json(patch(PLATFORMS + "/0/primary"), "{\"isPrimary\": true}")).andExpect(status().isBadRequest());
        asAlice(json(patch(PLATFORMS + "/" + alicesPc + "/primary"), "{}")).andExpect(status().isBadRequest());
        assertThat(platforms.findById(alicesPc).orElseThrow().getIsPrimary()).isTrue();
    }
}
