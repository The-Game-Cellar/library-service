package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.service.LibraryAdminService;
import com.thegamecellar.libraryservice.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin operations on the user-library catalog. Currently authenticated-access only —
 * INFRA20 tracks adding a {@code realm_admin} role check before public launch.
 */
@RestController
@RequestMapping("/api/v1/library/admin")
@RequiredArgsConstructor
public class AdminController {

    private final LibraryAdminService libraryAdminService;

    @PostMapping("/refresh-game-info")
    public ResponseEntity<Map<String, Object>> refreshGameInfo(Authentication authentication) {
        String bearerToken = JwtUtils.getBearerToken(authentication);
        return ResponseEntity.ok(libraryAdminService.refreshGameInfo(bearerToken));
    }
}
