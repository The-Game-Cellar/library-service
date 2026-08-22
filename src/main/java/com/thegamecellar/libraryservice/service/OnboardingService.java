package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.dto.OnboardingStatusDTO;
import com.thegamecellar.libraryservice.model.entity.UserOnboarding;
import com.thegamecellar.libraryservice.repository.UserOnboardingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserOnboardingRepository userOnboardingRepository;

    public OnboardingStatusDTO getStatus(String userId) {
        return userOnboardingRepository.findById(userId)
                .map(row -> new OnboardingStatusDTO(true, row.getCompletedAt()))
                .orElseGet(() -> new OnboardingStatusDTO(false, null));
    }

    // Idempotent: a second call keeps the original timestamp rather than moving it.
    @Transactional
    public OnboardingStatusDTO markCompleted(String userId) {
        UserOnboarding row = userOnboardingRepository.findById(userId)
                .orElseGet(() -> userOnboardingRepository.save(
                        UserOnboarding.builder().userId(userId).build()));
        return new OnboardingStatusDTO(true, row.getCompletedAt());
    }
}
