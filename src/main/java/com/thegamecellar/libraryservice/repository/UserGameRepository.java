package com.thegamecellar.libraryservice.repository;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserGameRepository extends JpaRepository<UserGame, Long> {

    List<UserGame> findByUserId(String userId);

    List<UserGame> findByUserIdAndStatus(String userId, GameStatus status);

    Optional<UserGame> findByUserIdAndIgdbGameId(String userId, Integer igdbGameId);

    boolean existsByUserIdAndIgdbGameId(String userId, Integer igdbGameId);

    Optional<UserGame> findByIdAndUserId(Long id, String userId);

    @Query("""
            SELECT g FROM UserGame g
            WHERE g.userId = :userId
            AND (:status IS NULL OR g.status = :status)
            AND (:platform IS NULL OR g.platform = :platform)
            AND (:search IS NULL OR LOWER(g.gameName) LIKE :search)
            AND (:genre IS NULL OR LOWER(g.genres) LIKE :genre)
            """)
    List<UserGame> findByUserIdWithFilters(
            @Param("userId") String userId,
            @Param("status") GameStatus status,
            @Param("platform") String platform,
            @Param("search") String search,
            @Param("genre") String genre
    );

    @Query("""
            SELECT g FROM UserGame g
            WHERE g.status IN ('BACKLOG', 'PLAYING')
            AND g.updatedAt < :threshold
            """)
    List<UserGame> findAllEligibleForDusty(@Param("threshold") LocalDateTime threshold);

    long deleteByUserId(String userId);
}