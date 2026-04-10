package com.thegamecellar.libraryservice.model.dto;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateGameRequest {
    private GameStatus status;
    private Integer rating;
    private String platform;
    private LocalDateTime lastPlayed;
    private Integer playtime;
    private String notes;
}