package com.thegamecellar.libraryservice.repository;

import com.thegamecellar.libraryservice.model.entity.UserOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserOnboardingRepository extends JpaRepository<UserOnboarding, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserOnboarding o WHERE o.userId = :userId")
    int deleteByUserId(@Param("userId") String userId);
}
