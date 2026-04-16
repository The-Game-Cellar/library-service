package com.thegamecellar.libraryservice.model.entity;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_games")
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

    @Column(name = "rawg_game_id", nullable = false)
    private Integer rawgGameId;

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