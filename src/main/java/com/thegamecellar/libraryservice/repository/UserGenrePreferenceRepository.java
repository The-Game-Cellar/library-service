package com.thegamecellar.libraryservice.repository;

import com.thegamecellar.libraryservice.model.entity.UserGenrePreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserGenrePreferenceRepository extends JpaRepository<UserGenrePreference, Long> {

    List<UserGenrePreference> findByUserId(String userId);

    // flush + clear so DELETE runs before saveAll INSERT in the replace-all txn; otherwise Hibernate orders
    // inserts before deletes and the (user_id, genre_name) unique constraint fires on re-insert.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserGenrePreference p WHERE p.userId = :userId")
    int deleteByUserId(@Param("userId") String userId);
}
