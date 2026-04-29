package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.dto.AddPlatformRequest;
import com.thegamecellar.libraryservice.model.dto.UserPlatformDTO;
import com.thegamecellar.libraryservice.service.PlatformService;
import com.thegamecellar.libraryservice.util.JwtUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/library/platforms")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformService platformService;

    @GetMapping
    public ResponseEntity<List<UserPlatformDTO>> getPlatforms(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(platformService.getPlatforms(userId));
    }

    @PostMapping
    public ResponseEntity<UserPlatformDTO> addPlatform(
            Authentication authentication,
            @Valid @RequestBody AddPlatformRequest request) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(platformService.addPlatform(userId, request));
    }

    @DeleteMapping("/{platformId}")
    public ResponseEntity<Void> removePlatform(Authentication authentication, @Min(1) @PathVariable Long platformId) {
        String userId = JwtUtils.getUserId(authentication);
        platformService.removePlatform(userId, platformId);
        return ResponseEntity.noContent().build();
    }
}