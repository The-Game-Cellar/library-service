package com.thegamecellar.libraryservice.model.dto;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UpdateGameRequest {
    private GameStatus status;
    @Min(1) @Max(10)
    private Integer rating;
    // `platforms` replaces the whole list; `platform` alone (the v1 client) replaces it with that one
    private List<String> platforms;
    private String platform;
    private LocalDateTime lastPlayed;
    @Min(0)
    private Integer playtime;
    private String notes;
}