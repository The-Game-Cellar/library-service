package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.exception.PlatformAlreadyAddedException;
import com.thegamecellar.libraryservice.exception.PlatformNotFoundException;
import com.thegamecellar.libraryservice.model.dto.AddPlatformRequest;
import com.thegamecellar.libraryservice.model.dto.UserPlatformDTO;
import com.thegamecellar.libraryservice.model.entity.UserPlatform;
import com.thegamecellar.libraryservice.repository.UserPlatformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public void removePlatform(String userId, Long platformId) {
        UserPlatform platform = userPlatformRepository.findByIdAndUserId(platformId, userId)
                .orElseThrow(() -> new PlatformNotFoundException(platformId));
        userPlatformRepository.delete(platform);
    }

    private UserPlatformDTO toDTO(UserPlatform platform) {
        return UserPlatformDTO.builder()
                .id(platform.getId())
                .platformName(platform.getPlatformName())
                .isPrimary(platform.getIsPrimary())
                .build();
    }
}