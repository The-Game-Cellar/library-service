package com.thegamecellar.libraryservice.repository;

import com.thegamecellar.libraryservice.model.entity.UserPlatform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPlatformRepository extends JpaRepository<UserPlatform, Long> {

    List<UserPlatform> findByUserId(String userId);

    boolean existsByUserIdAndPlatformName(String userId, String platformName);

    Optional<UserPlatform> findByIdAndUserId(Long id, String userId);
}
