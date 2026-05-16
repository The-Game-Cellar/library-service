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
        int genrePreferenceCount,
        int tagPreferenceCount,
        List<UserGameDTO> games,
        List<UserPlatformDTO> platforms,
        List<UserGenrePreferenceDTO> genrePreferences,
        List<UserTagPreferenceDTO> tagPreferences
) {
    public static AccountExportDTO of(String userId,
                                      List<UserGameDTO> games,
                                      List<UserPlatformDTO> platforms,
                                      List<UserGenrePreferenceDTO> genrePreferences,
                                      List<UserTagPreferenceDTO> tagPreferences) {
        return AccountExportDTO.builder()
                .userId(userId)
                .exportedAt(Instant.now().toString())
                .gameCount(games.size())
                .platformCount(platforms.size())
                .genrePreferenceCount(genrePreferences.size())
                .tagPreferenceCount(tagPreferences.size())
                .games(games)
                .platforms(platforms)
                .genrePreferences(genrePreferences)
                .tagPreferences(tagPreferences)
                .build();
    }
}
