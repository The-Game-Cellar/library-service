package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.dto.AccountDeletionDTO;
import com.thegamecellar.libraryservice.service.AccountService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// The gateway's retry job reads unfinished deletions here and reports each identity it has
// removed. No user JWT: the user is disabled or gone by the time this runs. Same two layers
// as the other internal paths, no gateway route plus the X-Internal-Token shared secret.
@Validated
@RestController
@RequestMapping("/internal/library/account-deletions")
@RequiredArgsConstructor
public class InternalAccountDeletionController {

    private final AccountService accountService;

    @GetMapping("/pending")
    public ResponseEntity<List<AccountDeletionDTO>> pending() {
        return ResponseEntity.ok(accountService.pendingDeletions());
    }

    @PostMapping("/{userId}/complete")
    public ResponseEntity<Void> complete(@NotBlank @PathVariable String userId) {
        accountService.completeDeletion(userId);
        return ResponseEntity.noContent().build();
    }
}
