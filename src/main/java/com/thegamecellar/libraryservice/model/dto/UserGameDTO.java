package com.thegamecellar.libraryservice.model.dto;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGameDTO {
    private Long id;
    private Integer rawgGameId;
    private String gameName;
    private String backgroundImage;
    private GameStatus status;
    private Integer rating;
    private String platform;
    private LocalDateTime dateAdded;
    private LocalDateTime lastPlayed;
    private Integer playtime;
    private String notes;
}