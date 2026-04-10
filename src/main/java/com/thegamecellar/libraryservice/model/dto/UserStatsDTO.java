package com.thegamecellar.libraryservice.model.dto;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsDTO {
    private long totalGames;
    private Map<GameStatus, Long> byStatus;
    private Double averageRating;
    private long totalRated;
}