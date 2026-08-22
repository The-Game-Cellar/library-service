package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.dto.OnboardingStatusDTO;
import com.thegamecellar.libraryservice.service.OnboardingService;
import com.thegamecellar.libraryservice.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/library/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping
    public ResponseEntity<OnboardingStatusDTO> getStatus(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(onboardingService.getStatus(userId));
    }

    @PostMapping
    public ResponseEntity<OnboardingStatusDTO> markCompleted(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(onboardingService.markCompleted(userId));
    }
}
