package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.AccountExportDTO;
import com.thegamecellar.libraryservice.model.dto.UserGameDTO;
import com.thegamecellar.libraryservice.model.dto.UserPlatformDTO;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import com.thegamecellar.libraryservice.repository.UserPlatformRepository;
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
    private final LibraryService libraryService;
    private final PlatformService platformService;

    /**
     * Purge all data owned by {@code userId} from {@code library_db}. Returns
     * the number of rows removed across both tables.
     */
    @Transactional
    public PurgeResult purgeUser(String userId) {
        long games = userGameRepository.deleteByUserId(userId);
        long platforms = userPlatformRepository.deleteByUserId(userId);
        log.info("Account purge complete for userId={}: removed {} games + {} platforms", userId, games, platforms);
        return new PurgeResult(games, platforms);
    }

    /**
     * Snapshot all data owned by {@code userId} into a portable JSON-friendly
     * DTO — fulfils GDPR right-to-portability.
     */
    public AccountExportDTO exportUser(String userId) {
        List<UserGameDTO> games = libraryService.getGames(userId, null, null, null, null);
        List<UserPlatformDTO> platforms = platformService.getPlatforms(userId);
        return AccountExportDTO.of(userId, games, platforms);
    }

    public record PurgeResult(long gamesRemoved, long platformsRemoved) {}
}
