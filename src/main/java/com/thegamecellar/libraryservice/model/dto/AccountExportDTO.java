package com.thegamecellar.libraryservice.model.dto;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.entity.UserGenrePreference;
import com.thegamecellar.libraryservice.model.entity.UserPlatform;
import com.thegamecellar.libraryservice.model.entity.UserReleaseYearPreference;
import com.thegamecellar.libraryservice.model.entity.UserTagPreference;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Every column of every row kept for the user, as stored, apart from user_id, which userId
// already carries. AccountServiceTest fails when an entity gains a field this file leaves out.
@Builder
public record AccountExportDTO(
        String userId,
        String exportedAt,
        int gameCount,
        int platformCount,
        int genrePreferenceCount,
        int tagPreferenceCount,
        int releaseYearPreferenceCount,
        LocalDateTime onboardingCompletedAt,
        List<ExportedGame> games,
        List<ExportedPlatform> platforms,
        List<ExportedGenrePreference> genrePreferences,
        List<ExportedTagPreference> tagPreferences,
        List<ExportedReleaseYearPreference> releaseYearPreferences
) {
    public static AccountExportDTO of(String userId,
                                      LocalDateTime onboardingCompletedAt,
                                      List<ExportedGame> games,
                                      List<ExportedPlatform> platforms,
                                      List<ExportedGenrePreference> genrePreferences,
                                      List<ExportedTagPreference> tagPreferences,
                                      List<ExportedReleaseYearPreference> releaseYearPreferences) {
        return AccountExportDTO.builder()
                .userId(userId)
                .exportedAt(Instant.now().toString())
                .gameCount(games.size())
                .platformCount(platforms.size())
                .genrePreferenceCount(genrePreferences.size())
                .tagPreferenceCount(tagPreferences.size())
                .releaseYearPreferenceCount(releaseYearPreferences.size())
                .onboardingCompletedAt(onboardingCompletedAt)
                .games(games)
                .platforms(platforms)
                .genrePreferences(genrePreferences)
                .tagPreferences(tagPreferences)
                .releaseYearPreferences(releaseYearPreferences)
                .build();
    }

    public record ExportedGame(Long id,
                               Integer igdbGameId,
                               String gameName,
                               GameStatus status,
                               GameStatus previousStatus,
                               BigDecimal rating,
                               List<String> platforms,
                               List<String> genres,
                               List<String> themes,
                               List<String> tags,
                               String released,
                               String backgroundImage,
                               String notes,
                               Integer playtime,
                               LocalDateTime dateAdded,
                               LocalDateTime lastPlayed,
                               LocalDateTime statusChangedAt,
                               LocalDateTime metadataSyncedAt,
                               LocalDateTime createdAt,
                               LocalDateTime updatedAt) {
        public static ExportedGame of(UserGame g) {
            return new ExportedGame(g.getId(), g.getIgdbGameId(), g.getGameName(), g.getStatus(),
                    g.getPreviousStatus(), g.getRating(), new ArrayList<>(g.getPlatforms()),
                    new ArrayList<>(g.getGenres()), new ArrayList<>(g.getThemes()), new ArrayList<>(g.getTags()),
                    g.getReleased(), g.getBackgroundImage(), g.getNotes(), g.getPlaytime(),
                    g.getDateAdded(), g.getLastPlayed(), g.getStatusChangedAt(), g.getMetadataSyncedAt(),
                    g.getCreatedAt(), g.getUpdatedAt());
        }
    }

    public record ExportedPlatform(Long id, String platformName, Boolean isPrimary) {
        public static ExportedPlatform of(UserPlatform p) {
            return new ExportedPlatform(p.getId(), p.getPlatformName(), p.getIsPrimary());
        }
    }

    public record ExportedGenrePreference(Long id, String genreName, LocalDateTime createdAt) {
        public static ExportedGenrePreference of(UserGenrePreference p) {
            return new ExportedGenrePreference(p.getId(), p.getGenreName(), p.getCreatedAt());
        }
    }

    public record ExportedTagPreference(Long id, String tagName, LocalDateTime createdAt) {
        public static ExportedTagPreference of(UserTagPreference p) {
            return new ExportedTagPreference(p.getId(), p.getTagName(), p.getCreatedAt());
        }
    }

    public record ExportedReleaseYearPreference(Long id, String bucketLabel, LocalDateTime createdAt) {
        public static ExportedReleaseYearPreference of(UserReleaseYearPreference p) {
            return new ExportedReleaseYearPreference(p.getId(), p.getBucketLabel(), p.getCreatedAt());
        }
    }
}
