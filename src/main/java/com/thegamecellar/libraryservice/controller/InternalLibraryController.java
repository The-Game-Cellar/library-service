package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.dto.UserGameDTO;
import com.thegamecellar.libraryservice.model.dto.UserGenrePreferenceDTO;
import com.thegamecellar.libraryservice.model.dto.UserPlatformDTO;
import com.thegamecellar.libraryservice.model.dto.UserReleaseYearPreferenceDTO;
import com.thegamecellar.libraryservice.model.dto.UserTagPreferenceDTO;
import com.thegamecellar.libraryservice.service.GenrePreferenceService;
import com.thegamecellar.libraryservice.service.LibraryService;
import com.thegamecellar.libraryservice.service.PlatformService;
import com.thegamecellar.libraryservice.service.ReleaseYearPreferenceService;
import com.thegamecellar.libraryservice.service.TagPreferenceService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Worker compute path (rec-service) calls these without a user JWT. Protected by two layers:
// (1) api-gateway has no route for /internal/** so it cannot be reached via the public 8000 port,
// (2) InternalAuthFilter enforces the X-Internal-Token shared secret so direct hits on 8082 also
// fail without the env-supplied token. Bearer-token argument deliberately null in
// LibraryService.getGames so the metadata-heal path is skipped (heal stays user-driven).
@Validated
@RestController
@RequestMapping("/internal/library/users/{userId}")
@RequiredArgsConstructor
public class InternalLibraryController {

    private final LibraryService libraryService;
    private final PlatformService platformService;
    private final GenrePreferenceService genrePreferenceService;
    private final TagPreferenceService tagPreferenceService;
    private final ReleaseYearPreferenceService releaseYearPreferenceService;

    @GetMapping("/games")
    public ResponseEntity<List<UserGameDTO>> getGames(@NotBlank @PathVariable String userId) {
        return ResponseEntity.ok(libraryService.getGames(userId, null, null, null, null, null));
    }

    @GetMapping("/platforms")
    public ResponseEntity<List<UserPlatformDTO>> getPlatforms(@NotBlank @PathVariable String userId) {
        return ResponseEntity.ok(platformService.getPlatforms(userId));
    }

    @GetMapping("/preferences/genres")
    public ResponseEntity<List<UserGenrePreferenceDTO>> getGenrePreferences(@NotBlank @PathVariable String userId) {
        return ResponseEntity.ok(genrePreferenceService.getPreferences(userId));
    }

    @GetMapping("/preferences/tags")
    public ResponseEntity<List<UserTagPreferenceDTO>> getTagPreferences(@NotBlank @PathVariable String userId) {
        return ResponseEntity.ok(tagPreferenceService.getPreferences(userId));
    }

    @GetMapping("/preferences/release-years")
    public ResponseEntity<List<UserReleaseYearPreferenceDTO>> getReleaseYearPreferences(@NotBlank @PathVariable String userId) {
        return ResponseEntity.ok(releaseYearPreferenceService.getPreferences(userId));
    }
}
