package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.dto.UpdateGenrePreferencesRequest;
import com.thegamecellar.libraryservice.model.dto.UserGenrePreferenceDTO;
import com.thegamecellar.libraryservice.service.GenrePreferenceService;
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
@RequestMapping("/api/v1/library/genre-preferences")
@RequiredArgsConstructor
public class GenrePreferenceController {

    private final GenrePreferenceService genrePreferenceService;

    @GetMapping
    public ResponseEntity<List<UserGenrePreferenceDTO>> getPreferences(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(genrePreferenceService.getPreferences(userId));
    }

    @PutMapping
    public ResponseEntity<List<UserGenrePreferenceDTO>> replacePreferences(
            Authentication authentication,
            @Valid @RequestBody UpdateGenrePreferencesRequest request) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(genrePreferenceService.replacePreferences(userId, request.getGenres()));
    }
}
