package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.AccountExportDTO;
import com.thegamecellar.libraryservice.model.dto.UserGameDTO;
import com.thegamecellar.libraryservice.model.dto.UserPlatformDTO;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import com.thegamecellar.libraryservice.repository.UserGenrePreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserPlatformRepository;
import com.thegamecellar.libraryservice.repository.UserTagPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Account-level operations cutting across {@link LibraryService} and
 * {@link PlatformService}. Handles GDPR right-to-deletion (purge) and
 * right-to-portability (export) for a single user.
 */
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

    /**
     * Purge all data owned by {@code userId} from {@code library_db}. Returns
     * the number of rows removed across every table that stores user-keyed data
     * (games, platforms, genre preferences, tag preferences).
     */
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

    /**
     * Snapshot all data owned by {@code userId} into a portable JSON-friendly
     * DTO — fulfils GDPR right-to-portability.
     */
    public AccountExportDTO exportUser(String userId) {
        List<UserGameDTO> games = libraryService.getGames(userId, null, null, null, null, null);
        List<UserPlatformDTO> platforms = platformService.getPlatforms(userId);
        return AccountExportDTO.of(userId, games, platforms);
    }

    public record PurgeResult(long gamesRemoved, long platformsRemoved, long genrePreferencesRemoved, long tagPreferencesRemoved) {}
}
