package com.thegamecellar.libraryservice.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.util.Ratings;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UpdateGameRequest {
    private GameStatus status;
    @DecimalMin("0.5") @DecimalMax("10")
    private BigDecimal rating;
    // `platforms` replaces the whole list; `platform` alone (the v1 client) replaces it with that one
    private List<String> platforms;
    private String platform;
    private LocalDateTime lastPlayed;
    @Min(0)
    private Integer playtime;
    private String notes;

    @JsonIgnore
    @AssertTrue(message = "rating must be a half step between 0.5 and 10")
    public boolean isRatingOnHalfStep() {
        return Ratings.onHalfStep(rating);
    }
}