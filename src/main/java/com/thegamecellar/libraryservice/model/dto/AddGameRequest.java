package com.thegamecellar.libraryservice.model.dto;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddGameRequest {
    @NotNull
    private Integer igdbGameId;
    @NotBlank
    private String gameName;
    @NotNull
    private GameStatus status;
    @NotBlank
    private String platform;
    @Min(1) @Max(10)
    private Integer rating;
    private String notes;
}