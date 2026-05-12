package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.dto.UpdateTagPreferencesRequest;
import com.thegamecellar.libraryservice.model.dto.UserTagPreferenceDTO;
import com.thegamecellar.libraryservice.service.TagPreferenceService;
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
@RequestMapping("/api/v1/library/tag-preferences")
@RequiredArgsConstructor
public class TagPreferenceController {

    private final TagPreferenceService tagPreferenceService;

    @GetMapping
    public ResponseEntity<List<UserTagPreferenceDTO>> getPreferences(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(tagPreferenceService.getPreferences(userId));
    }

    @PutMapping
    public ResponseEntity<List<UserTagPreferenceDTO>> replacePreferences(
            Authentication authentication,
            @Valid @RequestBody UpdateTagPreferencesRequest request) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(tagPreferenceService.replacePreferences(userId, request.getTags()));
    }
}
