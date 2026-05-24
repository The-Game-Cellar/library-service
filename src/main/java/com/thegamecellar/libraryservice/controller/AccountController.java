package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.dto.AccountExportDTO;
import com.thegamecellar.libraryservice.service.AccountService;
import com.thegamecellar.libraryservice.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GDPR-driven account-wide endpoints. {@code userId} always pulled from the
 * JWT, never from path or body.
 */
@RestController
@RequestMapping("/api/v1/library/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteAccount(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        AccountService.PurgeResult result = accountService.purgeUser(userId);
        return ResponseEntity.ok(Map.of(
                "message", "Account data purged",
                "gamesRemoved", result.gamesRemoved(),
                "platformsRemoved", result.platformsRemoved(),
                "genrePreferencesRemoved", result.genrePreferencesRemoved(),
                "tagPreferencesRemoved", result.tagPreferencesRemoved()
        ));
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountExportDTO> exportAccount(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(accountService.exportUser(userId));
    }
}
