package com.thegamecellar.libraryservice.repository;

import com.thegamecellar.libraryservice.model.entity.UserPlatform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPlatformRepository extends JpaRepository<UserPlatform, Long> {

    List<UserPlatform> findByUserId(String userId);

    boolean existsByUserIdAndPlatformName(String userId, String platformName);

    Optional<UserPlatform> findByIdAndUserId(Long id, String userId);

    long deleteByUserId(String userId);

    // ON CONFLICT rather than exists-then-save: two writes adding the same platform at once would
    // otherwise hit the (user_id, platform_name) constraint, and the exception rolls back the entry.
    @Modifying
    @Query(value = "INSERT INTO user_platforms (user_id, platform_name, is_primary) VALUES (:userId, :platformName, false) "
            + "ON CONFLICT (user_id, platform_name) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("userId") String userId, @Param("platformName") String platformName);
}
