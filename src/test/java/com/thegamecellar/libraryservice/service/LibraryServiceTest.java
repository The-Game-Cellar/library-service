package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.exception.GameAlreadyInCollectionException;
import com.thegamecellar.libraryservice.exception.GameNotFoundException;
import com.thegamecellar.libraryservice.model.dto.AddGameRequest;
import com.thegamecellar.libraryservice.model.dto.UpdateGameRequest;
import com.thegamecellar.libraryservice.model.dto.UserGameDTO;
import com.thegamecellar.libraryservice.model.dto.UserStatsDTO;
import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {

    @Mock
    private UserGameRepository userGameRepository;

    @Mock
    private GameServiceClient gameServiceClient;

    @InjectMocks
    private LibraryService libraryService;

    private static final String USER_ID = "user-123";
    private static final String OTHER_USER_ID = "other-user-456";

    private UserGame buildGame(Long id, String userId, GameStatus status) {
        return UserGame.builder()
                .id(id)
                .userId(userId)
                .igdbGameId(3328)
                .gameName("The Witcher 3")
                .status(status)
                .platform("PC")
                .dateAdded(LocalDateTime.now().minusDays(10))
                .build();
    }

    @Test
    void shouldAddGameToCollection() {
        AddGameRequest request = new AddGameRequest();
        request.setIgdbGameId(3328);
        request.setGameName("The Witcher 3");
        request.setStatus(GameStatus.BACKLOG);
        request.setPlatform("PC");

        UserGame saved = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        when(userGameRepository.existsByUserIdAndIgdbGameId(USER_ID, 3328)).thenReturn(false);
        when(gameServiceClient.getGameInfo(eq(3328), anyString())).thenReturn(
                new GameServiceClient.GameInfo("The Witcher 3", "https://example.com/witcher.jpg",
                        List.of("RPG", "Action"), List.of("Fantasy"), List.of("open world", "story rich")));
        when(userGameRepository.save(any())).thenReturn(saved);

        UserGameDTO result = libraryService.addGame(USER_ID, request, "Bearer test-token");

        assertThat(result.getGameName()).isEqualTo("The Witcher 3");
        assertThat(result.getStatus()).isEqualTo(GameStatus.BACKLOG);
        verify(userGameRepository).save(any());
    }

    @Test
    void shouldCacheThemesAndTagsOnAdd() {
        AddGameRequest request = new AddGameRequest();
        request.setIgdbGameId(3328);
        request.setGameName("The Witcher 3");
        request.setStatus(GameStatus.BACKLOG);
        request.setPlatform("PC");

        when(userGameRepository.existsByUserIdAndIgdbGameId(USER_ID, 3328)).thenReturn(false);
        when(gameServiceClient.getGameInfo(eq(3328), anyString())).thenReturn(
                new GameServiceClient.GameInfo("The Witcher 3", null,
                        List.of("RPG"), List.of("Fantasy", "Historical"), List.of("open world", "story rich")));
        when(userGameRepository.save(any())).thenAnswer(inv -> {
            UserGame g = inv.getArgument(0);
            g.setId(1L);
            return g;
        });

        UserGameDTO result = libraryService.addGame(USER_ID, request, "Bearer token");

        assertThat(result.getThemes()).containsExactly("Fantasy", "Historical");
        assertThat(result.getTags()).containsExactly("open world", "story rich");
    }

    @Test
    void shouldUseGameNameFromGameServiceNotFromRequest() {
        AddGameRequest request = new AddGameRequest();
        request.setIgdbGameId(3328);
        request.setGameName("Spoofed Name");
        request.setStatus(GameStatus.BACKLOG);
        request.setPlatform("PC");

        when(userGameRepository.existsByUserIdAndIgdbGameId(USER_ID, 3328)).thenReturn(false);
        when(gameServiceClient.getGameInfo(eq(3328), anyString())).thenReturn(
                new GameServiceClient.GameInfo("The Witcher 3", "https://example.com/witcher.jpg",
                        List.of("RPG"), List.of(), List.of()));
        when(userGameRepository.save(any())).thenAnswer(inv -> {
            UserGame g = inv.getArgument(0);
            g.setId(1L);
            return g;
        });

        UserGameDTO result = libraryService.addGame(USER_ID, request, "Bearer token");

        assertThat(result.getGameName()).isEqualTo("The Witcher 3");
    }

    @Test
    void shouldFallBackToRequestGameNameWhenGameServiceIsDown() {
        AddGameRequest request = new AddGameRequest();
        request.setIgdbGameId(3328);
        request.setGameName("The Witcher 3");
        request.setStatus(GameStatus.BACKLOG);
        request.setPlatform("PC");

        when(userGameRepository.existsByUserIdAndIgdbGameId(USER_ID, 3328)).thenReturn(false);
        when(gameServiceClient.getGameInfo(eq(3328), anyString())).thenReturn(
                new GameServiceClient.GameInfo(null, null, List.of(), List.of(), List.of()));
        when(userGameRepository.save(any())).thenAnswer(inv -> {
            UserGame g = inv.getArgument(0);
            g.setId(1L);
            return g;
        });

        UserGameDTO result = libraryService.addGame(USER_ID, request, "Bearer token");

        assertThat(result.getGameName()).isEqualTo("The Witcher 3");
    }

    @Test
    void shouldThrow409IfGameAlreadyExists() {
        AddGameRequest request = new AddGameRequest();
        request.setIgdbGameId(3328);
        request.setGameName("The Witcher 3");
        request.setStatus(GameStatus.BACKLOG);
        request.setPlatform("PC");

        when(userGameRepository.existsByUserIdAndIgdbGameId(USER_ID, 3328)).thenReturn(true);

        assertThatThrownBy(() -> libraryService.addGame(USER_ID, request, "Bearer test-token"))
                .isInstanceOf(GameAlreadyInCollectionException.class);

        verify(userGameRepository, never()).save(any());
    }

    @Test
    void shouldOnlyReturnGamesForCurrentUser() {
        UserGame game = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        when(userGameRepository.findByUserIdWithFilters(USER_ID, null, null, null, null))
                .thenReturn(List.of(game));

        List<UserGameDTO> result = libraryService.getGames(USER_ID, null, null, null, null, null);

        assertThat(result).hasSize(1);
        verify(userGameRepository).findByUserIdWithFilters(USER_ID, null, null, null, null);
    }

    @Test
    void shouldFilterByGenre() {
        UserGame rpgGame = UserGame.builder()
                .id(1L).userId(USER_ID).igdbGameId(1).gameName("Witcher 3")
                .status(GameStatus.BACKLOG).platform("PC").genres("RPG,Action")
                .dateAdded(LocalDateTime.now().minusDays(1)).build();
        when(userGameRepository.findByUserIdWithFilters(eq(USER_ID), isNull(), isNull(), isNull(), eq("%rpg%")))
                .thenReturn(List.of(rpgGame));

        List<UserGameDTO> result = libraryService.getGames(USER_ID, null, null, null, "RPG", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGenres()).contains("RPG");
    }

    @Test
    void shouldHealStaleMetadataOnRead() {
        UserGame stale = UserGame.builder()
                .id(1L).userId(USER_ID).igdbGameId(3328).gameName("The Witcher 3")
                .status(GameStatus.BACKLOG).platform("PC")
                .dateAdded(LocalDateTime.now()).build();
        when(userGameRepository.findByUserIdWithFilters(eq(USER_ID), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(stale));
        when(gameServiceClient.getGameInfo(eq(3328), eq("Bearer t"))).thenReturn(
                new GameServiceClient.GameInfo("The Witcher 3", null,
                        List.of("RPG"), List.of("Fantasy"), List.of("open world")));
        when(userGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<UserGameDTO> result = libraryService.getGames(USER_ID, null, null, null, null, "Bearer t");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getThemes()).containsExactly("Fantasy");
        assertThat(result.get(0).getTags()).containsExactly("open world");
        verify(userGameRepository).save(any(UserGame.class));
    }

    @Test
    void shouldNotHealRowsAlreadySynced() {
        UserGame fresh = UserGame.builder()
                .id(1L).userId(USER_ID).igdbGameId(3328).gameName("The Witcher 3")
                .status(GameStatus.BACKLOG).platform("PC")
                .genres("RPG").themes("Fantasy").tags("open world")
                .dateAdded(LocalDateTime.now()).build();
        when(userGameRepository.findByUserIdWithFilters(eq(USER_ID), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(fresh));

        libraryService.getGames(USER_ID, null, null, null, null, "Bearer t");

        verify(gameServiceClient, never()).getGameInfo(anyInt(), anyString());
        verify(userGameRepository, never()).save(any());
    }

    @Test
    void shouldNotHealWhenBearerTokenAbsent() {
        UserGame stale = UserGame.builder()
                .id(1L).userId(USER_ID).igdbGameId(3328).gameName("The Witcher 3")
                .status(GameStatus.BACKLOG).platform("PC")
                .dateAdded(LocalDateTime.now()).build();
        when(userGameRepository.findByUserIdWithFilters(eq(USER_ID), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(stale));

        libraryService.getGames(USER_ID, null, null, null, null, null);

        verify(gameServiceClient, never()).getGameInfo(anyInt(), anyString());
        verify(userGameRepository, never()).save(any());
    }

    @Test
    void shouldFilterByStatus() {
        UserGame backlogGame = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        when(userGameRepository.findByUserIdAndStatus(USER_ID, GameStatus.BACKLOG))
                .thenReturn(List.of(backlogGame));

        List<UserGameDTO> result = libraryService.getByStatus(USER_ID, GameStatus.BACKLOG);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(GameStatus.BACKLOG);
    }

    @Test
    void shouldUpdateGameStatus() {
        UserGame game = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        UpdateGameRequest request = new UpdateGameRequest();
        request.setStatus(GameStatus.PLAYING);
        request.setRating(9);

        when(userGameRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(game));
        when(userGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserGameDTO result = libraryService.updateGame(USER_ID, 1L, request);

        assertThat(result.getStatus()).isEqualTo(GameStatus.PLAYING);
        assertThat(result.getRating()).isEqualTo(9);
        assertThat(result.getLastPlayed()).isNotNull();
    }

    @Test
    void shouldNotSetLastPlayedWhenStatusIsNotPlaying() {
        UserGame game = buildGame(1L, USER_ID, GameStatus.DUSTY);
        UpdateGameRequest request = new UpdateGameRequest();
        request.setStatus(GameStatus.BACKLOG);

        when(userGameRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(game));
        when(userGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserGameDTO result = libraryService.updateGame(USER_ID, 1L, request);

        assertThat(result.getStatus()).isEqualTo(GameStatus.BACKLOG);
        assertThat(result.getLastPlayed()).isNull();
    }

    @Test
    void shouldThrow404WhenUpdatingOtherUsersGame() {
        assertThatThrownBy(() -> libraryService.updateGame(USER_ID, 1L, new UpdateGameRequest()))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test
    void shouldRemoveGame() {
        UserGame game = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        when(userGameRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(game));

        libraryService.removeGame(USER_ID, 1L);

        verify(userGameRepository).delete(game);
    }

    @Test
    void shouldReturnDustyGames() {
        UserGame dustyGame = buildGame(1L, USER_ID, GameStatus.DUSTY);
        when(userGameRepository.findByUserIdAndStatus(USER_ID, GameStatus.DUSTY))
                .thenReturn(List.of(dustyGame));

        List<UserGameDTO> result = libraryService.getDustyGames(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(GameStatus.DUSTY);
        verify(userGameRepository).findByUserIdAndStatus(USER_ID, GameStatus.DUSTY);
    }

    @Test
    void shouldRejectManualDustyStatusUpdate() {
        UserGame game = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        UpdateGameRequest request = new UpdateGameRequest();
        request.setStatus(GameStatus.DUSTY);

        when(userGameRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> libraryService.updateGame(USER_ID, 1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("auto-assigned");
    }

    @Test
    void shouldCalculateStatsCorrectly() {
        List<UserGame> games = List.of(
                buildGameWithRating(1L, GameStatus.BACKLOG, null),
                buildGameWithRating(2L, GameStatus.BACKLOG, null),
                buildGameWithRating(3L, GameStatus.COMPLETED, 8),
                buildGameWithRating(4L, GameStatus.COMPLETED, 10),
                buildGameWithRating(5L, GameStatus.PLAYING, 9)
        );
        when(userGameRepository.findByUserId(USER_ID)).thenReturn(games);

        UserStatsDTO stats = libraryService.getStats(USER_ID);

        assertThat(stats.getTotalGames()).isEqualTo(5);
        assertThat(stats.getTotalRated()).isEqualTo(3);
        assertThat(stats.getAverageRating()).isEqualTo(9.0);
        assertThat(stats.getByStatus().get(GameStatus.BACKLOG)).isEqualTo(2L);
        assertThat(stats.getByStatus().get(GameStatus.COMPLETED)).isEqualTo(2L);
        assertThat(stats.getByStatus().get(GameStatus.PLAYING)).isEqualTo(1L);
    }

    private UserGame buildGameWithRating(Long id, GameStatus status, Integer rating) {
        return UserGame.builder()
                .id(id)
                .userId(USER_ID)
                .igdbGameId(id.intValue())
                .gameName("Game " + id)
                .status(status)
                .platform("PC")
                .rating(rating)
                .dateAdded(LocalDateTime.now().minusDays(10))
                .build();
    }
}
