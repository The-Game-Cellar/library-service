package com.thegamecellar.libraryservice.model.dto;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateGameRequest {
    private GameStatus status;
    @Min(1) @Max(10)
    private Integer rating;
    private String platform;
    private LocalDateTime lastPlayed;
    @Min(0)
    private Integer playtime;
    private String notes;
}