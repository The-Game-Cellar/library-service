package com.thegamecellar.libraryservice.repository;

import com.thegamecellar.libraryservice.model.entity.AccountDeletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AccountDeletionRepository extends JpaRepository<AccountDeletion, String> {

    List<AccountDeletion> findByIdentityDeletedAtIsNullAndRequestedAtBefore(LocalDateTime cutoff);
}
