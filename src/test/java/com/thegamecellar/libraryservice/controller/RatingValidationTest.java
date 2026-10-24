package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.support.LibraryServiceTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A rating is 0.5 to 10 in half steps. The range comes from @DecimalMin / @DecimalMax on the
// request, the step from Ratings.onHalfStep behind an @AssertTrue; both answer 400 before the
// service is reached, and an accepted half step survives the NUMERIC(3,1) column.
class RatingValidationTest extends LibraryServiceTestBase {

    private long aliceEntry() {
        return entry(ALICE, 1001, "Hades", GameStatus.BACKLOG, "PC").getId();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.5", "1", "6.5", "9.5", "10", "10.0"})
    void update_withAHalfStepRating_isAccepted(String rating) throws Exception {
        long id = aliceEntry();

        mvc.perform(put("/api/v1/library/games/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":" + rating + "}"))
                .andExpect(status().isOk());

        assertThat(games.findById(id).orElseThrow().getRating()).isEqualByComparingTo(new BigDecimal(rating));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.4", "6.3", "6.75", "10.5", "11"})
    void update_withARatingOffTheScale_returns400(String rating) throws Exception {
        long id = aliceEntry();

        mvc.perform(put("/api/v1/library/games/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":" + rating + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(greaterThan(0)));

        assertThat(games.findById(id).orElseThrow().getRating()).isNull();
    }

    @Test
    void add_withARatingOffTheHalfStep_returns400() throws Exception {
        mvc.perform(post("/api/v1/library/games")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"igdbGameId\":1,\"gameName\":\"Hades\",\"status\":\"COMPLETED\",\"platform\":\"PC\",\"rating\":7.3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
        assertThat(games.count()).isZero();
    }

    @Test
    void add_withAHalfStepRating_isAccepted() throws Exception {
        mvc.perform(post("/api/v1/library/games")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"igdbGameId\":1,\"gameName\":\"Hades\",\"status\":\"COMPLETED\",\"platform\":\"PC\",\"rating\":7.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(7.5));
    }
}
