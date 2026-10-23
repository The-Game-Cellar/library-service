package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.exception.PlatformAlreadyAddedException;
import com.thegamecellar.libraryservice.exception.PlatformNotFoundException;
import com.thegamecellar.libraryservice.model.dto.AddPlatformRequest;
import com.thegamecellar.libraryservice.model.dto.UserPlatformDTO;
import com.thegamecellar.libraryservice.model.entity.UserPlatform;
import com.thegamecellar.libraryservice.repository.UserPlatformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformService {

    private final UserPlatformRepository userPlatformRepository;

    public List<UserPlatformDTO> getPlatforms(String userId) {
        return userPlatformRepository.findByUserId(userId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public UserPlatformDTO addPlatform(String userId, AddPlatformRequest request) {
        if (userPlatformRepository.existsByUserIdAndPlatformName(userId, request.getPlatformName())) {
            throw new PlatformAlreadyAddedException(request.getPlatformName());
        }
        UserPlatform platform = UserPlatform.builder()
                .userId(userId)
                .platformName(request.getPlatformName())
                .isPrimary(request.getIsPrimary() != null ? request.getIsPrimary() : false)
                .build();
        return toDTO(userPlatformRepository.save(platform));
    }

    // A library entry's platforms join the user's own; one already there keeps its primary flag.
    @Transactional
    public void addMissing(String userId, List<String> platformNames) {
        for (String name : platformNames) {
            userPlatformRepository.insertIfAbsent(userId, name);
        }
    }

    public void removePlatform(String userId, Long platformId) {
        UserPlatform platform = userPlatformRepository.findByIdAndUserId(platformId, userId)
                .orElseThrow(() -> new PlatformNotFoundException(platformId));
        userPlatformRepository.delete(platform);
    }

    public UserPlatformDTO setPrimary(String userId, Long platformId, boolean isPrimary) {
        UserPlatform platform = userPlatformRepository.findByIdAndUserId(platformId, userId)
                .orElseThrow(() -> new PlatformNotFoundException(platformId));
        platform.setIsPrimary(isPrimary);
        return toDTO(userPlatformRepository.save(platform));
    }

    private UserPlatformDTO toDTO(UserPlatform platform) {
        return UserPlatformDTO.builder()
                .id(platform.getId())
                .platformName(platform.getPlatformName())
                .isPrimary(platform.getIsPrimary())
                .build();
    }
}