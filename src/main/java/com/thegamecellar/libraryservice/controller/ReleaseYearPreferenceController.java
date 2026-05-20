package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.dto.UpdateReleaseYearPreferencesRequest;
import com.thegamecellar.libraryservice.model.dto.UserReleaseYearPreferenceDTO;
import com.thegamecellar.libraryservice.service.ReleaseYearPreferenceService;
import com.thegamecellar.libraryservice.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/library/release-year-preferences")
@RequiredArgsConstructor
public class ReleaseYearPreferenceController {

    private final ReleaseYearPreferenceService releaseYearPreferenceService;

    @GetMapping
    public ResponseEntity<List<UserReleaseYearPreferenceDTO>> getPreferences(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(releaseYearPreferenceService.getPreferences(userId));
    }

    @PutMapping
    public ResponseEntity<List<UserReleaseYearPreferenceDTO>> replacePreferences(
            Authentication authentication,
            @Valid @RequestBody UpdateReleaseYearPreferencesRequest request) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(releaseYearPreferenceService.replacePreferences(userId, request.getBuckets()));
    }
}
