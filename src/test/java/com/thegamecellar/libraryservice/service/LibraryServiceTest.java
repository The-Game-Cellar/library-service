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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
                .rawgGameId(3328)
                .gameName("The Witcher 3")
                .status(status)
                .platform("PC")
                .dateAdded(LocalDateTime.now().minusDays(10))
                .build();
    }

    @Test
    void shouldAddGameToCollection() {
        AddGameRequest request = new AddGameRequest();
        request.setRawgGameId(3328);
        request.setGameName("The Witcher 3");
        request.setStatus(GameStatus.BACKLOG);
        request.setPlatform("PC");

        UserGame saved = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        when(userGameRepository.existsByUserIdAndRawgGameId(USER_ID, 3328)).thenReturn(false);
        when(userGameRepository.save(any())).thenReturn(saved);

        UserGameDTO result = libraryService.addGame(USER_ID, request);

        assertThat(result.getGameName()).isEqualTo("The Witcher 3");
        assertThat(result.getStatus()).isEqualTo(GameStatus.BACKLOG);
        verify(userGameRepository).save(any());
    }

    @Test
    void shouldThrow409IfGameAlreadyExists() {
        AddGameRequest request = new AddGameRequest();
        request.setRawgGameId(3328);
        request.setGameName("The Witcher 3");
        request.setStatus(GameStatus.BACKLOG);
        request.setPlatform("PC");

        when(userGameRepository.existsByUserIdAndRawgGameId(USER_ID, 3328)).thenReturn(true);

        assertThatThrownBy(() -> libraryService.addGame(USER_ID, request))
                .isInstanceOf(GameAlreadyInCollectionException.class);

        verify(userGameRepository, never()).save(any());
    }

    @Test
    void shouldOnlyReturnGamesForCurrentUser() {
        UserGame game = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        when(userGameRepository.findByUserIdWithFilters(USER_ID, null, null, null))
                .thenReturn(List.of(game));

        List<UserGameDTO> result = libraryService.getGames(USER_ID, null, null, null);

        assertThat(result).hasSize(1);
        verify(userGameRepository).findByUserIdWithFilters(USER_ID, null, null, null);
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

        when(userGameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(userGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserGameDTO result = libraryService.updateGame(USER_ID, 1L, request);

        assertThat(result.getStatus()).isEqualTo(GameStatus.PLAYING);
        assertThat(result.getRating()).isEqualTo(9);
    }

    @Test
    void shouldThrow404WhenUpdatingOtherUsersGame() {
        UserGame otherGame = buildGame(1L, OTHER_USER_ID, GameStatus.BACKLOG);
        when(userGameRepository.findById(1L)).thenReturn(Optional.of(otherGame));

        assertThatThrownBy(() -> libraryService.updateGame(USER_ID, 1L, new UpdateGameRequest()))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test
    void shouldRemoveGame() {
        UserGame game = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        when(userGameRepository.findById(1L)).thenReturn(Optional.of(game));

        libraryService.removeGame(USER_ID, 1L);

        verify(userGameRepository).delete(game);
    }

    @Test
    void shouldReturnForgottenGames() {
        UserGame oldGame = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        when(userGameRepository.findForgottenGames(eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(List.of(oldGame));

        List<UserGameDTO> result = libraryService.getForgottenGames(USER_ID, 90);

        assertThat(result).hasSize(1);
        verify(userGameRepository).findForgottenGames(eq(USER_ID), any(LocalDateTime.class));
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
                .rawgGameId(id.intValue())
                .gameName("Game " + id)
                .status(status)
                .platform("PC")
                .rating(rating)
                .dateAdded(LocalDateTime.now().minusDays(10))
                .build();
    }
}
