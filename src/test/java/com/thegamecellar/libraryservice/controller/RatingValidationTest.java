package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.service.LibraryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A rating is 0.5 to 10 in half steps. The range comes from @DecimalMin / @DecimalMax on the
// request, the step from Ratings.onHalfStep behind an @AssertTrue; both answer 400 before the
// service is reached.
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
class RatingValidationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LibraryService libraryService;

    @ParameterizedTest
    @ValueSource(strings = {"0.5", "1", "6.5", "9.5", "10", "10.0"})
    void update_withAHalfStepRating_isAccepted(String rating) throws Exception {
        mvc.perform(put("/api/v1/library/games/1")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":" + rating + "}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.4", "6.3", "6.75", "10.5", "11"})
    void update_withARatingOffTheScale_returns400(String rating) throws Exception {
        mvc.perform(put("/api/v1/library/games/1")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":" + rating + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(greaterThan(0)));
    }

    @Test
    void add_withARatingOffTheHalfStep_returns400() throws Exception {
        mvc.perform(post("/api/v1/library/games")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"igdbGameId\":1,\"gameName\":\"Hades\",\"status\":\"COMPLETED\",\"platform\":\"PC\",\"rating\":7.3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void add_withAHalfStepRating_isAccepted() throws Exception {
        mvc.perform(post("/api/v1/library/games")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"igdbGameId\":1,\"gameName\":\"Hades\",\"status\":\"COMPLETED\",\"platform\":\"PC\",\"rating\":7.5}"))
                .andExpect(status().isCreated());
    }
}
