package com.thegamecellar.libraryservice.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.util.Platforms;
import com.thegamecellar.libraryservice.util.Ratings;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AddGameRequest {
    @NotNull
    private Integer igdbGameId;
    @NotBlank @Size(max = 255)
    private String gameName;
    @NotNull
    private GameStatus status;
    // The platforms the entry is owned on, first is the main one; `platform` alone is the v1 client's field
    private List<@Size(max = 255) String> platforms;
    @Size(max = 255)
    private String platform;
    @DecimalMin("0.5") @DecimalMax("10")
    private BigDecimal rating;
    // The app's notes field stops at the same length
    @Size(max = 10_000)
    private String notes;

    @JsonIgnore
    @AssertTrue(message = "at least one platform is required")
    public boolean isPlatformGiven() {
        return !Platforms.normalise(platforms, platform).isEmpty();
    }

    @JsonIgnore
    @AssertTrue(message = "rating must be a half step between 0.5 and 10")
    public boolean isRatingOnHalfStep() {
        return Ratings.onHalfStep(rating);
    }
}
