package com.thegamecellar.libraryservice.repository;

import com.thegamecellar.libraryservice.model.entity.UserTagPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserTagPreferenceRepository extends JpaRepository<UserTagPreference, Long> {

    List<UserTagPreference> findByUserId(String userId);

    // Bulk DELETE via JPQL with flushAutomatically + clearAutomatically so the SQL DELETE runs
    // before the subsequent saveAll INSERTs in the replace-all transaction. Without these, the
    // Hibernate action queue orders inserts before deletes within the same transaction and the
    // (user_id, tag_name) unique constraint fires when re-inserting an existing name.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserTagPreference p WHERE p.userId = :userId")
    int deleteByUserId(@Param("userId") String userId);
}
