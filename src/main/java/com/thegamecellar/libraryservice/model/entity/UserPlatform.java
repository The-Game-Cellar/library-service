package com.thegamecellar.libraryservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "user_platforms",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "platform_name"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPlatform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "platform_name", nullable = false)
    private String platformName;

    @Column(name = "is_primary")
    private Boolean isPrimary;
}
