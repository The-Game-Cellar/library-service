package com.thegamecellar.libraryservice.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.util.Platforms;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AddGameRequest {
    @NotNull
    private Integer igdbGameId;
    @NotBlank
    private String gameName;
    @NotNull
    private GameStatus status;
    // The platforms the entry is owned on, first is the main one; `platform` alone is the v1 client's field
    private List<String> platforms;
    private String platform;
    @Min(1) @Max(10)
    private Integer rating;
    private String notes;

    @JsonIgnore
    @AssertTrue(message = "at least one platform is required")
    public boolean isPlatformGiven() {
        return !Platforms.normalise(platforms, platform).isEmpty();
    }
}
