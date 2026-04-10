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

    Optional<UserGame> findByUserIdAndRawgGameId(String userId, Integer rawgGameId);

    boolean existsByUserIdAndRawgGameId(String userId, Integer rawgGameId);

    @Query("""
            SELECT g FROM UserGame g
            WHERE g.userId = :userId
            AND (:status IS NULL OR g.status = :status)
            AND (:platform IS NULL OR g.platform = :platform)
            AND (:search IS NULL OR LOWER(g.gameName) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    List<UserGame> findByUserIdWithFilters(
            @Param("userId") String userId,
            @Param("status") GameStatus status,
            @Param("platform") String platform,
            @Param("search") String search
    );

    @Query("""
            SELECT g FROM UserGame g
            WHERE g.userId = :userId
            AND g.status IN ('BACKLOG', 'PLAYING')
            AND g.dateAdded < :threshold
            AND (g.lastPlayed IS NULL OR g.lastPlayed < :threshold)
            """)
    List<UserGame> findForgottenGames(
            @Param("userId") String userId,
            @Param("threshold") LocalDateTime threshold
    );
}