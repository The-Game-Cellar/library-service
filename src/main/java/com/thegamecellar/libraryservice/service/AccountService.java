package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.AccountExportDTO;
import com.thegamecellar.libraryservice.model.dto.UserGameDTO;
import com.thegamecellar.libraryservice.model.dto.UserGenrePreferenceDTO;
import com.thegamecellar.libraryservice.model.dto.UserPlatformDTO;
import com.thegamecellar.libraryservice.model.dto.UserTagPreferenceDTO;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import com.thegamecellar.libraryservice.repository.UserGenrePreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserPlatformRepository;
import com.thegamecellar.libraryservice.repository.UserTagPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// GDPR right-to-deletion + right-to-portability for a single user across every user-keyed table.
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserGameRepository userGameRepository;
    private final UserPlatformRepository userPlatformRepository;
    private final UserGenrePreferenceRepository userGenrePreferenceRepository;
    private final UserTagPreferenceRepository userTagPreferenceRepository;
    private final LibraryService libraryService;
    private final PlatformService platformService;
    private final GenrePreferenceService genrePreferenceService;
    private final TagPreferenceService tagPreferenceService;

    @Transactional
    public PurgeResult purgeUser(String userId) {
        long games = userGameRepository.deleteByUserId(userId);
        long platforms = userPlatformRepository.deleteByUserId(userId);
        long genrePreferences = userGenrePreferenceRepository.deleteByUserId(userId);
        long tagPreferences = userTagPreferenceRepository.deleteByUserId(userId);
        log.info("Account purge complete for userId={}: removed {} games + {} platforms + {} genre prefs + {} tag prefs",
                userId, games, platforms, genrePreferences, tagPreferences);
        return new PurgeResult(games, platforms, genrePreferences, tagPreferences);
    }

    public AccountExportDTO exportUser(String userId) {
        List<UserGameDTO> games = libraryService.getGames(userId, null, null, null, null, null);
        List<UserPlatformDTO> platforms = platformService.getPlatforms(userId);
        List<UserGenrePreferenceDTO> genrePreferences = genrePreferenceService.getPreferences(userId);
        List<UserTagPreferenceDTO> tagPreferences = tagPreferenceService.getPreferences(userId);
        return AccountExportDTO.of(userId, games, platforms, genrePreferences, tagPreferences);
    }

    public record PurgeResult(long gamesRemoved, long platformsRemoved, long genrePreferencesRemoved, long tagPreferencesRemoved) {}
}
