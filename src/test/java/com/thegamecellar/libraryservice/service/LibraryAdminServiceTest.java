package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryAdminServiceTest {

    private UserGameRepository userGameRepository;
    private GameServiceClient gameServiceClient;
    private LibraryAdminService service;

    @BeforeEach
    void setUp() {
        userGameRepository = mock(UserGameRepository.class);
        gameServiceClient = mock(GameServiceClient.class);
        service = new LibraryAdminService(userGameRepository, gameServiceClient);
    }

    @Test
    void refreshGameInfo_updates_genres_when_upstream_set_differs() {
        UserGame existing = userGame(1L, 100, List.of("RPG", "Adventure"));
        Page<UserGame> page = new PageImpl<>(List.of(existing));
        when(userGameRepository.findAll(any(Pageable.class))).thenReturn(page);

        // Game Service now returns the game with derived "Action" added.
        when(gameServiceClient.getGameInfo(eq(100), anyString())).thenReturn(
                new GameServiceClient.GameInfo("Some Game", null,
                        List.of("RPG", "Adventure", "Action"),
                        List.of(), List.of(), null));

        Map<String, Object> result = service.refreshGameInfo("Bearer token");

        assertThat(result).containsEntry("examined", 1L);
        assertThat(result).containsEntry("updated", 1L);
        assertThat(result).containsEntry("genresChanged", 1L);
        assertThat(existing.getGenres()).contains("Action");
        assertThat(existing.getMetadataSyncedAt()).isNotNull();
        verify(userGameRepository).save(existing);
    }

    @Test
    void refreshGameInfo_skips_save_when_upstream_set_matches_cached() {
        UserGame existing = userGame(1L, 100, List.of("RPG", "Adventure"));
        existing.setThemes(new ArrayList<>(List.of("Fantasy")));
        existing.setTags(new ArrayList<>());
        existing.setReleased("");
        Page<UserGame> page = new PageImpl<>(List.of(existing));
        when(userGameRepository.findAll(any(Pageable.class))).thenReturn(page);

        when(gameServiceClient.getGameInfo(eq(100), anyString())).thenReturn(
                new GameServiceClient.GameInfo("Some Game", null,
                        List.of("Adventure", "RPG"),       // same set, different order
                        List.of("Fantasy"),
                        List.of(), null));

        service.refreshGameInfo("Bearer token");

        verify(userGameRepository, never()).save(any(UserGame.class));
    }

    @Test
    void refreshGameInfo_skips_row_when_game_service_returns_empty() {
        UserGame existing = userGame(1L, 100, List.of("RPG"));
        Page<UserGame> page = new PageImpl<>(List.of(existing));
        when(userGameRepository.findAll(any(Pageable.class))).thenReturn(page);

        when(gameServiceClient.getGameInfo(anyInt(), anyString())).thenReturn(
                new GameServiceClient.GameInfo(null, null, List.of(), List.of(), List.of(), null));

        Map<String, Object> result = service.refreshGameInfo("Bearer token");

        assertThat(result).containsEntry("gameServiceMisses", 1L);
        assertThat(result).containsEntry("updated", 0L);
        verify(userGameRepository, never()).save(any(UserGame.class));
    }

    @Test
    void refreshGameInfo_counts_each_dimension_independently() {
        UserGame g1 = userGame(1L, 100, List.of("RPG"));
        g1.setThemes(new ArrayList<>(List.of("Fantasy")));
        g1.setTags(new ArrayList<>());
        UserGame g2 = userGame(2L, 200, List.of("Action"));
        g2.setThemes(new ArrayList<>());
        g2.setTags(new ArrayList<>());
        Page<UserGame> page = new PageImpl<>(List.of(g1, g2));
        when(userGameRepository.findAll(any(Pageable.class))).thenReturn(page);

        // g1: only genres change
        when(gameServiceClient.getGameInfo(eq(100), anyString())).thenReturn(
                new GameServiceClient.GameInfo("G1", null,
                        List.of("RPG", "Sci-fi"), List.of("Fantasy"), List.of(), null));
        // g2: themes change too
        when(gameServiceClient.getGameInfo(eq(200), anyString())).thenReturn(
                new GameServiceClient.GameInfo("G2", null,
                        List.of("Action"), List.of("Sci-fi"), List.of(), null));

        Map<String, Object> result = service.refreshGameInfo("Bearer token");

        assertThat(result).containsEntry("examined", 2L);
        assertThat(result).containsEntry("updated", 2L);
        assertThat(result).containsEntry("genresChanged", 1L);
        assertThat(result).containsEntry("themesChanged", 1L);
        assertThat(result).containsEntry("tagsChanged", 0L);
    }

    @Test
    void refreshGameInfo_walks_multiple_pages() {
        // Force two pages by sizing each page at 1 with total=2.
        Pageable p0 = org.springframework.data.domain.PageRequest.of(0, 1);
        Pageable p1 = org.springframework.data.domain.PageRequest.of(1, 1);
        Page<UserGame> page0 = new PageImpl<>(List.of(userGame(1L, 100, List.of())), p0, 2);
        Page<UserGame> page1 = new PageImpl<>(List.of(userGame(2L, 200, List.of())), p1, 2);
        when(userGameRepository.findAll(any(Pageable.class))).thenReturn(page0).thenReturn(page1);

        lenient().when(gameServiceClient.getGameInfo(anyInt(), anyString())).thenReturn(
                new GameServiceClient.GameInfo("Test", null, List.of(), List.of(), List.of(), null));

        service.refreshGameInfo("Bearer token");

        verify(userGameRepository, times(2)).findAll(any(Pageable.class));
    }

    private UserGame userGame(Long id, Integer igdbId, List<String> genres) {
        UserGame g = new UserGame();
        g.setId(id);
        g.setIgdbGameId(igdbId);
        g.setGameName("Game " + id);
        g.setGenres(new ArrayList<>(genres));
        g.setThemes(new ArrayList<>());
        g.setTags(new ArrayList<>());
        return g;
    }
}
