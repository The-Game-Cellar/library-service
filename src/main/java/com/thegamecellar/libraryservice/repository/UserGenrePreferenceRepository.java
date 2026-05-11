package com.thegamecellar.libraryservice.repository;

import com.thegamecellar.libraryservice.model.entity.UserGenrePreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserGenrePreferenceRepository extends JpaRepository<UserGenrePreference, Long> {

    List<UserGenrePreference> findByUserId(String userId);

    long deleteByUserId(String userId);
}
