package com.thegamecellar.libraryservice.model.entity;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_games", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "igdb_game_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "igdb_game_id", nullable = false)
    private Integer igdbGameId;

    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GameStatus status;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "date_added", updatable = false)
    private LocalDateTime dateAdded;

    @Column(name = "last_played")
    private LocalDateTime lastPlayed;

    @Column(name = "playtime")
    private Integer playtime;

    @Column(name = "background_image")
    private String backgroundImage;

    @Column(name = "genres")
    private String genres;

    @Column(name = "themes", columnDefinition = "TEXT")
    private String themes;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "released", length = 20)
    private String released;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.dateAdded = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}