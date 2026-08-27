package com.thegamecellar.libraryservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Deletion ledger row. Written beside the purge, completed when the gateway reports the
// Keycloak user gone. Deliberately not purged with the rest of the user's rows: it is the
// record that the erasure was requested, and the retry job keys on it.
@Entity
@Table(name = "account_deletions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountDeletion {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "identity_deleted_at")
    private LocalDateTime identityDeletedAt;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
    }
}
