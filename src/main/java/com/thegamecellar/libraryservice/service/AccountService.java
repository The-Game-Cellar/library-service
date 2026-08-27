package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.AccountDeletionDTO;
import com.thegamecellar.libraryservice.model.dto.AccountExportDTO;
import com.thegamecellar.libraryservice.model.dto.UserGameDTO;
import com.thegamecellar.libraryservice.model.dto.UserGenrePreferenceDTO;
import com.thegamecellar.libraryservice.model.dto.UserPlatformDTO;
import com.thegamecellar.libraryservice.model.dto.UserReleaseYearPreferenceDTO;
import com.thegamecellar.libraryservice.model.dto.UserTagPreferenceDTO;
import com.thegamecellar.libraryservice.model.entity.AccountDeletion;
import com.thegamecellar.libraryservice.repository.AccountDeletionRepository;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import com.thegamecellar.libraryservice.repository.UserGenrePreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserOnboardingRepository;
import com.thegamecellar.libraryservice.repository.UserPlatformRepository;
import com.thegamecellar.libraryservice.repository.UserReleaseYearPreferenceRepository;
import com.thegamecellar.libraryservice.repository.UserTagPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// GDPR right-to-deletion + right-to-portability for a single user across every user-keyed table.
// Every entity carrying a userId must have its repository injected here and purged below;
// AccountServiceTest scans the entity package and fails when one is missing. The one
// exception is the deletion ledger, which must outlive the purge.
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    // A row younger than this may still be inside the request that wrote it, with the
    // gateway about to delete the identity itself; the retry job leaves those alone.
    static final long PENDING_GRACE_SECONDS = 60;

    private final AccountDeletionRepository accountDeletionRepository;
    private final UserGameRepository userGameRepository;
    private final UserPlatformRepository userPlatformRepository;
    private final UserGenrePreferenceRepository userGenrePreferenceRepository;
    private final UserTagPreferenceRepository userTagPreferenceRepository;
    private final UserReleaseYearPreferenceRepository userReleaseYearPreferenceRepository;
    private final UserOnboardingRepository userOnboardingRepository;
    private final LibraryService libraryService;
    private final PlatformService platformService;
    private final GenrePreferenceService genrePreferenceService;
    private final TagPreferenceService tagPreferenceService;
    private final ReleaseYearPreferenceService releaseYearPreferenceService;

    // Ledger and purge commit together, so there is no state where the library is gone and
    // nothing records that the identity still has to follow. A second request for the same
    // user keeps the original requested_at.
    @Transactional
    public PurgeResult requestDeletion(String userId) {
        AccountDeletion ledger = accountDeletionRepository.findById(userId)
                .orElseGet(() -> AccountDeletion.builder().userId(userId).build());
        accountDeletionRepository.save(ledger);
        return purgeUser(userId);
    }

    @Transactional(readOnly = true)
    public List<AccountDeletionDTO> pendingDeletions() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(PENDING_GRACE_SECONDS);
        return accountDeletionRepository.findByIdentityDeletedAtIsNullAndRequestedAtBefore(cutoff).stream()
                .map(row -> new AccountDeletionDTO(row.getUserId(), row.getRequestedAt()))
                .toList();
    }

    // Idempotent: the gateway may report the same identity gone more than once.
    @Transactional
    public void completeDeletion(String userId) {
        accountDeletionRepository.findById(userId)
                .filter(row -> row.getIdentityDeletedAt() == null)
                .ifPresent(row -> {
                    row.setIdentityDeletedAt(LocalDateTime.now());
                    log.info("Account deletion complete for userId={}: identity confirmed gone", userId);
                });
    }

    @Transactional
    public PurgeResult purgeUser(String userId) {
        long games = userGameRepository.deleteByUserId(userId);
        long platforms = userPlatformRepository.deleteByUserId(userId);
        long genrePreferences = userGenrePreferenceRepository.deleteByUserId(userId);
        long tagPreferences = userTagPreferenceRepository.deleteByUserId(userId);
        long releaseYearPreferences = userReleaseYearPreferenceRepository.deleteByUserId(userId);
        long onboarding = userOnboardingRepository.deleteByUserId(userId);
        log.info("Account purge complete for userId={}: removed {} games + {} platforms + {} genre prefs + {} tag prefs + {} release-year prefs + {} onboarding rows",
                userId, games, platforms, genrePreferences, tagPreferences, releaseYearPreferences, onboarding);
        return new PurgeResult(games, platforms, genrePreferences, tagPreferences, releaseYearPreferences, onboarding);
    }

    public AccountExportDTO exportUser(String userId) {
        List<UserGameDTO> games = libraryService.getGames(userId, null, null, null, null, null);
        List<UserPlatformDTO> platforms = platformService.getPlatforms(userId);
        List<UserGenrePreferenceDTO> genrePreferences = genrePreferenceService.getPreferences(userId);
        List<UserTagPreferenceDTO> tagPreferences = tagPreferenceService.getPreferences(userId);
        List<UserReleaseYearPreferenceDTO> releaseYearPreferences = releaseYearPreferenceService.getPreferences(userId);
        return AccountExportDTO.of(userId, games, platforms, genrePreferences, tagPreferences, releaseYearPreferences);
    }

    public record PurgeResult(long gamesRemoved,
                              long platformsRemoved,
                              long genrePreferencesRemoved,
                              long tagPreferencesRemoved,
                              long releaseYearPreferencesRemoved,
                              long onboardingRemoved) {}
}
