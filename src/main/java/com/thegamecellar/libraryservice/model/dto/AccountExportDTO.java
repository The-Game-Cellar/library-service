package com.thegamecellar.libraryservice.model.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record AccountExportDTO(
        String userId,
        String exportedAt,
        int gameCount,
        int platformCount,
        List<UserGameDTO> games,
        List<UserPlatformDTO> platforms
) {
    public static AccountExportDTO of(String userId, List<UserGameDTO> games, List<UserPlatformDTO> platforms) {
        return AccountExportDTO.builder()
                .userId(userId)
                .exportedAt(Instant.now().toString())
                .gameCount(games.size())
                .platformCount(platforms.size())
                .games(games)
                .platforms(platforms)
                .build();
    }
}
