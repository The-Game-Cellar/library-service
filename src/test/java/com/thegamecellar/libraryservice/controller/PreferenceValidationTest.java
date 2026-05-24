package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.service.GenrePreferenceService;
import com.thegamecellar.libraryservice.service.ReleaseYearPreferenceService;
import com.thegamecellar.libraryservice.service.TagPreferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.datasource.password=",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://unused"
})
class PreferenceValidationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GenrePreferenceService genrePreferenceService;

    @MockitoBean
    private TagPreferenceService tagPreferenceService;

    @MockitoBean
    private ReleaseYearPreferenceService releaseYearPreferenceService;

    @Test
    void genrePreferences_withEmptyStringElement_returns400() throws Exception {
        mvc.perform(put("/api/v1/library/genre-preferences")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genres\":[\"\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(greaterThan(0)));
    }

    @Test
    void genrePreferences_withNullList_returns400() throws Exception {
        mvc.perform(put("/api/v1/library/genre-preferences")
                        .with(jwt().jwt(j -> j.subject("user-1")))
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
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"" + tooLong + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.length()").value(greaterThan(0)));
    }

    @Test
    void releaseYearPreferences_withEmptyStringElement_returns400() throws Exception {
        mvc.perform(put("/api/v1/library/release-year-preferences")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buckets\":[\"\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.length()").value(greaterThan(0)));
    }
}
