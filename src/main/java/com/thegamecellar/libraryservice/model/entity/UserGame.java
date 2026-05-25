package com.thegamecellar.libraryservice.model.entity;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_game_genres", joinColumns = @JoinColumn(name = "user_game_id"))
    @Column(name = "genres", length = 100)
    @BatchSize(size = 200)
    @Builder.Default
    private List<String> genres = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_game_themes", joinColumns = @JoinColumn(name = "user_game_id"))
    @Column(name = "themes", length = 100)
    @BatchSize(size = 200)
    @Builder.Default
    private List<String> themes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_game_tags", joinColumns = @JoinColumn(name = "user_game_id"))
    @Column(name = "tags", length = 100)
    @BatchSize(size = 200)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Column(name = "released", length = 20)
    private String released;

    @Column(name = "notes")
    private String notes;

    @Column(name = "metadata_synced_at")
    private LocalDateTime metadataSyncedAt;

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
