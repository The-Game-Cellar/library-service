package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.dto.*;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.service.LibraryService;
import com.thegamecellar.libraryservice.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService libraryService;

    @GetMapping("/games")
    public ResponseEntity<List<UserGameDTO>> getGames(
            Authentication authentication,
            @RequestParam(required = false) GameStatus status,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String search) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(libraryService.getGames(userId, status, platform, search));
    }

    @GetMapping("/games/{gameId}")
    public ResponseEntity<UserGameDTO> getGame(Authentication authentication, @PathVariable Long gameId) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(libraryService.getGame(userId, gameId));
    }

    @PostMapping("/games")
    public ResponseEntity<UserGameDTO> addGame(
            Authentication authentication,
            @RequestBody AddGameRequest request) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(libraryService.addGame(userId, request));
    }

    @PutMapping("/games/{gameId}")
    public ResponseEntity<UserGameDTO> updateGame(
            Authentication authentication,
            @PathVariable Long gameId,
            @RequestBody UpdateGameRequest request) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(libraryService.updateGame(userId, gameId, request));
    }

    @DeleteMapping("/games/{gameId}")
    public ResponseEntity<Void> removeGame(Authentication authentication, @PathVariable Long gameId) {
        String userId = JwtUtils.getUserId(authentication);
        libraryService.removeGame(userId, gameId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/backlog")
    public ResponseEntity<List<UserGameDTO>> getBacklog(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(libraryService.getByStatus(userId, GameStatus.BACKLOG));
    }

    @GetMapping("/wishlist")
    public ResponseEntity<List<UserGameDTO>> getWishlist(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(libraryService.getByStatus(userId, GameStatus.WISHLIST));
    }

    @GetMapping("/playing")
    public ResponseEntity<List<UserGameDTO>> getPlaying(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(libraryService.getByStatus(userId, GameStatus.PLAYING));
    }

    @GetMapping("/completed")
    public ResponseEntity<List<UserGameDTO>> getCompleted(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(libraryService.getByStatus(userId, GameStatus.COMPLETED));
    }

    @GetMapping("/stats")
    public ResponseEntity<UserStatsDTO> getStats(Authentication authentication) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(libraryService.getStats(userId));
    }

    @GetMapping("/forgotten")
    public ResponseEntity<List<UserGameDTO>> getForgottenGames(
            Authentication authentication,
            @RequestParam(defaultValue = "90") int days) {
        String userId = JwtUtils.getUserId(authentication);
        return ResponseEntity.ok(libraryService.getForgottenGames(userId, days));
    }
}