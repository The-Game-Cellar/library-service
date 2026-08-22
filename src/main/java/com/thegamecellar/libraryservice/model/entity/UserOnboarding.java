package com.thegamecellar.libraryservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_onboarding")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserOnboarding {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (completedAt == null) {
            completedAt = LocalDateTime.now();
        }
    }
}
