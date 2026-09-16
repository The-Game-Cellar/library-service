package com.thegamecellar.libraryservice.model.dto;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGameDTO {
    private Long id;
    private Integer igdbGameId;
    private String gameName;
    private String backgroundImage;
    private List<String> genres;
    private List<String> themes;
    private List<String> tags;
    private String released;
    private GameStatus status;
    private BigDecimal rating;
    // `platform` is the first of `platforms`, kept for the v1 client
    private String platform;
    private List<String> platforms;
    private LocalDateTime dateAdded;
    private LocalDateTime lastPlayed;
    private LocalDateTime statusChangedAt;
    private GameStatus previousStatus;
    private Integer playtime;
    private String notes;
}