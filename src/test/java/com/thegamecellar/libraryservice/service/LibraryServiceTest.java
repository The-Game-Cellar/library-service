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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Mock
    private LibraryWritePublisher writePublisher;

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
                .platforms(new ArrayList<>(List.of("PC")))
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
                        List.of("RPG", "Action"), List.of("Fantasy"), List.of("open world", "story rich"), "2015-05-19"));
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
                        List.of("RPG"), List.of("Fantasy", "Historical"), List.of("open world", "story rich"), null));
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
    void shouldMarkMetadataSyncedAtOnAddWhenUpstreamResponds() {
        AddGameRequest request = new AddGameRequest();
        request.setIgdbGameId(3328);
        request.setGameName("The Witcher 3");
        request.setStatus(GameStatus.BACKLOG);
        request.setPlatform("PC");

        when(userGameRepository.existsByUserIdAndIgdbGameId(USER_ID, 3328)).thenReturn(false);
        when(gameServiceClient.getGameInfo(eq(3328), anyString())).thenReturn(
                new GameServiceClient.GameInfo("The Witcher 3", null,
                        List.of("RPG"), List.of(), List.of(), null));
        when(userGameRepository.save(any())).thenAnswer(inv -> {
            UserGame g = inv.getArgument(0);
            g.setId(1L);
            return g;
        });

        libraryService.addGame(USER_ID, request, "Bearer token");

        verify(userGameRepository).save(argThat(g -> g.getMetadataSyncedAt() != null));
    }

    @Test
    void shouldLeaveMetadataSyncedAtNullOnAddWhenUpstreamDown() {
        AddGameRequest request = new AddGameRequest();
        request.setIgdbGameId(3328);
        request.setGameName("The Witcher 3");
        request.setStatus(GameStatus.BACKLOG);
        request.setPlatform("PC");

        when(userGameRepository.existsByUserIdAndIgdbGameId(USER_ID, 3328)).thenReturn(false);
        when(gameServiceClient.getGameInfo(eq(3328), anyString())).thenReturn(
                new GameServiceClient.GameInfo(null, null, List.of(), List.of(), List.of(), null));
        when(userGameRepository.save(any())).thenAnswer(inv -> {
            UserGame g = inv.getArgument(0);
            g.setId(1L);
            return g;
        });

        libraryService.addGame(USER_ID, request, "Bearer token");

        verify(userGameRepository).save(argThat(g -> g.getMetadataSyncedAt() == null));
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
                        List.of("RPG"), List.of(), List.of(), null));
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
                new GameServiceClient.GameInfo(null, null, List.of(), List.of(), List.of(), null));
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
        game.setMetadataSyncedAt(LocalDateTime.now());
        when(userGameRepository.findByUserIdWithFilters(USER_ID, null, null, null))
                .thenReturn(List.of(game));

        List<UserGameDTO> result = libraryService.getGames(USER_ID, null, null, null, null, null);

        assertThat(result).hasSize(1);
        verify(userGameRepository).findByUserIdWithFilters(USER_ID, null, null, null);
    }

    @Test
    void shouldFilterByGenre() {
        UserGame rpgGame = UserGame.builder()
                .id(1L).userId(USER_ID).igdbGameId(1).gameName("Witcher 3")
                .status(GameStatus.BACKLOG).platforms(new ArrayList<>(List.of("PC")))
                .genres(new ArrayList<>(List.of("RPG", "Action")))
                .metadataSyncedAt(LocalDateTime.now())
                .dateAdded(LocalDateTime.now().minusDays(1)).build();
        when(userGameRepository.findByUserIdWithFilters(eq(USER_ID), isNull(), isNull(), eq("rpg")))
                .thenReturn(List.of(rpgGame));

        List<UserGameDTO> result = libraryService.getGames(USER_ID, null, null, null, "RPG", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGenres()).contains("RPG");
    }

    @Test
    void shouldPassExactLowercasedGenreKeyNotLikePattern() {
        when(userGameRepository.findByUserIdWithFilters(eq(USER_ID), isNull(), isNull(), eq("rpg")))
                .thenReturn(List.of());

        libraryService.getGames(USER_ID, null, null, null, "RPG", null);

        verify(userGameRepository).findByUserIdWithFilters(USER_ID, null, null, "rpg");
    }

    @Test
    void shouldHealStaleMetadataOnRead() {
        UserGame stale = UserGame.builder()
                .id(1L).userId(USER_ID).igdbGameId(3328).gameName("The Witcher 3")
                .status(GameStatus.BACKLOG).platforms(new ArrayList<>(List.of("PC")))
                .dateAdded(LocalDateTime.now()).build();
        when(userGameRepository.findByUserIdWithFilters(eq(USER_ID), isNull(), isNull(), isNull()))
                .thenReturn(List.of(stale));
        when(gameServiceClient.getGameInfo(eq(3328), eq("Bearer t"))).thenReturn(
                new GameServiceClient.GameInfo("The Witcher 3", null,
                        List.of("RPG"), List.of("Fantasy"), List.of("open world"), "2015-05-19"));
        when(userGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<UserGameDTO> result = libraryService.getGames(USER_ID, null, null, null, null, "Bearer t");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getThemes()).containsExactly("Fantasy");
        assertThat(result.get(0).getTags()).containsExactly("open world");
        assertThat(stale.getMetadataSyncedAt()).isNotNull();
        verify(userGameRepository).save(any(UserGame.class));
    }

    @Test
    void shouldNotHealRowsAlreadySynced() {
        UserGame fresh = UserGame.builder()
                .id(1L).userId(USER_ID).igdbGameId(3328).gameName("The Witcher 3")
                .status(GameStatus.BACKLOG).platforms(new ArrayList<>(List.of("PC")))
                .genres(new ArrayList<>(List.of("RPG")))
                .themes(new ArrayList<>(List.of("Fantasy")))
                .tags(new ArrayList<>(List.of("open world")))
                .released("2015-05-19")
                .metadataSyncedAt(LocalDateTime.now())
                .dateAdded(LocalDateTime.now()).build();
        when(userGameRepository.findByUserIdWithFilters(eq(USER_ID), isNull(), isNull(), isNull()))
                .thenReturn(List.of(fresh));

        libraryService.getGames(USER_ID, null, null, null, null, "Bearer t");

        verify(gameServiceClient, never()).getGameInfo(anyInt(), anyString());
        verify(userGameRepository, never()).save(any());
    }

    @Test
    void shouldNotHealWhenBearerTokenAbsent() {
        UserGame stale = UserGame.builder()
                .id(1L).userId(USER_ID).igdbGameId(3328).gameName("The Witcher 3")
                .status(GameStatus.BACKLOG).platforms(new ArrayList<>(List.of("PC")))
                .dateAdded(LocalDateTime.now()).build();
        when(userGameRepository.findByUserIdWithFilters(eq(USER_ID), isNull(), isNull(), isNull()))
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
        assertThat(result.getStatusChangedAt()).isNotNull();
    }

    @Test
    void shouldMoveStatusChangedAtOnlyOnARealStatusChange() {
        LocalDateTime longAgo = LocalDateTime.now().minusDays(40);
        UserGame game = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        game.setStatusChangedAt(longAgo);
        when(userGameRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(game));
        when(userGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateGameRequest ratingOnly = new UpdateGameRequest();
        ratingOnly.setRating(7);
        UserGameDTO afterRating = libraryService.updateGame(USER_ID, 1L, ratingOnly);
        assertThat(afterRating.getStatusChangedAt()).isEqualTo(longAgo);
        assertThat(afterRating.getPreviousStatus()).isNull();

        UpdateGameRequest sameStatus = new UpdateGameRequest();
        sameStatus.setStatus(GameStatus.BACKLOG);
        UserGameDTO afterSame = libraryService.updateGame(USER_ID, 1L, sameStatus);
        assertThat(afterSame.getStatusChangedAt()).isEqualTo(longAgo);
        assertThat(afterSame.getPreviousStatus()).isNull();

        UpdateGameRequest toPlaying = new UpdateGameRequest();
        toPlaying.setStatus(GameStatus.PLAYING);
        UserGameDTO afterChange = libraryService.updateGame(USER_ID, 1L, toPlaying);
        assertThat(afterChange.getStatusChangedAt()).isAfter(longAgo);
        assertThat(afterChange.getPreviousStatus()).isEqualTo(GameStatus.BACKLOG);
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
    void shouldReturnGameByIgdbId() {
        UserGame game = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        when(userGameRepository.findByUserIdAndIgdbGameId(USER_ID, 3328)).thenReturn(Optional.of(game));

        UserGameDTO result = libraryService.getGameByIgdbId(USER_ID, 3328);

        assertThat(result.getIgdbGameId()).isEqualTo(3328);
        assertThat(result.getGameName()).isEqualTo("The Witcher 3");
    }

    @Test
    void shouldThrow404WhenGameByIgdbIdNotInCollection() {
        when(userGameRepository.findByUserIdAndIgdbGameId(USER_ID, 9999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> libraryService.getGameByIgdbId(USER_ID, 9999))
                .isInstanceOf(GameNotFoundException.class)
                .hasMessageContaining("9999");
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

    @Test
    void shouldAccumulateGenresFromCollection() {
        List<UserGame> games = List.of(
                buildGameWithGenresAndPlatform(1L, List.of("RPG", "Action"), "PC"),
                buildGameWithGenresAndPlatform(2L, List.of("RPG", "Adventure"), "PC"),
                buildGameWithGenresAndPlatform(3L, List.of("Action"), "PlayStation 5"),
                buildGameWithGenresAndPlatform(4L, List.of("Strategy", "RPG"), "PC"),
                buildGameWithGenresAndPlatform(5L, List.of(), "Nintendo Switch")
        );
        when(userGameRepository.findByUserId(USER_ID)).thenReturn(games);

        UserStatsDTO stats = libraryService.getStats(USER_ID);

        assertThat(stats.getByGenre()).containsEntry("RPG", 3L);
        assertThat(stats.getByGenre()).containsEntry("Action", 2L);
        assertThat(stats.getByGenre()).containsEntry("Adventure", 1L);
        assertThat(stats.getByGenre()).containsEntry("Strategy", 1L);
        assertThat(stats.getByGenre()).hasSize(4);
    }

    @Test
    void shouldCountAGameUnderEachOfItsPlatforms() {
        UserGame onBoth = buildGameWithGenresAndPlatform(4L, List.of("RPG"), "PC");
        onBoth.setPlatforms(new ArrayList<>(List.of("PlayStation 5", "PC")));
        List<UserGame> games = List.of(
                buildGameWithGenresAndPlatform(1L, List.of("RPG"), "PC"),
                buildGameWithGenresAndPlatform(2L, List.of("Action"), "PC"),
                buildGameWithGenresAndPlatform(3L, List.of("Action"), "PlayStation 5"),
                onBoth
        );
        when(userGameRepository.findByUserId(USER_ID)).thenReturn(games);

        UserStatsDTO stats = libraryService.getStats(USER_ID);

        // Four games, five platform slots: the per-platform numbers sum past the total on purpose
        assertThat(stats.getTotalGames()).isEqualTo(4);
        assertThat(stats.getByPlatform()).containsEntry("PC", 3L);
        assertThat(stats.getByPlatform()).containsEntry("PlayStation 5", 2L);
        assertThat(stats.getByPlatform()).hasSize(2);
    }

    @Test
    void shouldReturnEmptyDistributionMapsForEmptyLibrary() {
        when(userGameRepository.findByUserId(USER_ID)).thenReturn(List.of());

        UserStatsDTO stats = libraryService.getStats(USER_ID);

        assertThat(stats.getByGenre()).isEmpty();
        assertThat(stats.getByPlatform()).isEmpty();
    }

    @Test
    void shouldListEveryPlatformAcrossEntriesOnce() {
        UserGame onBoth = buildGameWithGenresAndPlatform(4L, List.of("RPG"), "PC");
        onBoth.setPlatforms(new ArrayList<>(List.of("Nintendo Switch", "PC")));
        List<UserGame> games = List.of(
                buildGameWithGenresAndPlatform(1L, List.of("RPG"), "PlayStation 5"),
                buildGameWithGenresAndPlatform(2L, List.of("Action"), "PC"),
                buildGameWithGenresAndPlatform(3L, List.of("Action"), "PC"),
                onBoth
        );
        when(userGameRepository.findByUserId(USER_ID)).thenReturn(games);

        List<String> platforms = libraryService.getGamePlatforms(USER_ID);

        assertThat(platforms).containsExactly("Nintendo Switch", "PC", "PlayStation 5");
    }

    @Test
    void shouldKeepTheClientsPlatformOrderAndDropRepeatsAndBlanks() {
        AddGameRequest request = new AddGameRequest();
        request.setIgdbGameId(3328);
        request.setGameName("The Witcher 3");
        request.setStatus(GameStatus.BACKLOG);
        request.setPlatforms(List.of(" PlayStation 5 ", "PC", "PC", "  ", "PlayStation 5"));
        request.setPlatform("Xbox Series X|S");

        when(userGameRepository.existsByUserIdAndIgdbGameId(USER_ID, 3328)).thenReturn(false);
        when(gameServiceClient.getGameInfo(eq(3328), anyString())).thenReturn(
                new GameServiceClient.GameInfo(null, null, List.of(), List.of(), List.of(), null));
        when(userGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserGameDTO result = libraryService.addGame(USER_ID, request, "Bearer token");

        // The list wins over the single field; the first entry is the main platform
        assertThat(result.getPlatforms()).containsExactly("PlayStation 5", "PC");
        assertThat(result.getPlatform()).isEqualTo("PlayStation 5");
    }

    @Test
    void shouldAcceptTheSinglePlatformFieldFromTheOldClient() {
        AddGameRequest request = new AddGameRequest();
        request.setIgdbGameId(3328);
        request.setGameName("The Witcher 3");
        request.setStatus(GameStatus.BACKLOG);
        request.setPlatform("PC");

        when(userGameRepository.existsByUserIdAndIgdbGameId(USER_ID, 3328)).thenReturn(false);
        when(gameServiceClient.getGameInfo(eq(3328), anyString())).thenReturn(
                new GameServiceClient.GameInfo(null, null, List.of(), List.of(), List.of(), null));
        when(userGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserGameDTO result = libraryService.addGame(USER_ID, request, "Bearer token");

        assertThat(result.getPlatforms()).containsExactly("PC");
        assertThat(result.getPlatform()).isEqualTo("PC");
    }

    @Test
    void shouldRefuseToAddAGameWithoutAnyPlatform() {
        AddGameRequest request = new AddGameRequest();
        request.setIgdbGameId(3328);
        request.setGameName("The Witcher 3");
        request.setStatus(GameStatus.BACKLOG);
        request.setPlatforms(List.of("  "));

        when(userGameRepository.existsByUserIdAndIgdbGameId(USER_ID, 3328)).thenReturn(false);

        assertThat(request.isPlatformGiven()).isFalse();
        assertThatThrownBy(() -> libraryService.addGame(USER_ID, request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userGameRepository, never()).save(any());
    }

    @Test
    void shouldReplaceTheWholePlatformListOnUpdate() {
        UserGame game = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        UpdateGameRequest request = new UpdateGameRequest();
        request.setPlatforms(List.of("Nintendo Switch", "PC"));
        when(userGameRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(game));
        when(userGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserGameDTO result = libraryService.updateGame(USER_ID, 1L, request);

        assertThat(result.getPlatforms()).containsExactly("Nintendo Switch", "PC");
        assertThat(result.getPlatform()).isEqualTo("Nintendo Switch");
    }

    @Test
    void shouldReplaceTheListWithTheOldClientsSinglePlatform() {
        UserGame game = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        game.setPlatforms(new ArrayList<>(List.of("PC", "PlayStation 5")));
        UpdateGameRequest request = new UpdateGameRequest();
        request.setPlatform("Nintendo Switch");
        when(userGameRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(game));
        when(userGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserGameDTO result = libraryService.updateGame(USER_ID, 1L, request);

        assertThat(result.getPlatforms()).containsExactly("Nintendo Switch");
    }

    @Test
    void shouldLeavePlatformsAloneWhenTheUpdateDoesNotMentionThem() {
        UserGame game = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        game.setPlatforms(new ArrayList<>(List.of("PC", "PlayStation 5")));
        UpdateGameRequest request = new UpdateGameRequest();
        request.setRating(8);
        when(userGameRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(game));
        when(userGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserGameDTO result = libraryService.updateGame(USER_ID, 1L, request);

        assertThat(result.getPlatforms()).containsExactly("PC", "PlayStation 5");
    }

    @Test
    void shouldRejectAnUpdateThatLeavesNoPlatform() {
        UserGame game = buildGame(1L, USER_ID, GameStatus.BACKLOG);
        UpdateGameRequest request = new UpdateGameRequest();
        request.setPlatforms(List.of());
        when(userGameRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> libraryService.updateGame(USER_ID, 1L, request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userGameRepository, never()).save(any());
    }

    @Test
    void shouldFilterByAnyOfTheGivenPlatforms() {
        UserGame onPc = buildGameWithGenresAndPlatform(1L, List.of(), "PC");
        UserGame onSwitch = buildGameWithGenresAndPlatform(2L, List.of(), "Nintendo Switch");
        UserGame onBoth = buildGameWithGenresAndPlatform(3L, List.of(), "PlayStation 5");
        onBoth.setPlatforms(new ArrayList<>(List.of("PlayStation 5", "PC")));
        List.of(onPc, onSwitch, onBoth).forEach(g -> g.setMetadataSyncedAt(LocalDateTime.now()));
        when(userGameRepository.findByUserIdWithFilters(USER_ID, null, null, null))
                .thenReturn(List.of(onPc, onSwitch, onBoth));

        List<UserGameDTO> pc = libraryService.getGames(USER_ID, null, List.of("PC"), null, null, null);
        List<UserGameDTO> pcOrSwitch = libraryService.getGames(USER_ID, null, List.of("PC", " Nintendo Switch "), null, null, null);
        List<UserGameDTO> none = libraryService.getGames(USER_ID, null, List.of("Xbox 360"), null, null, null);
        List<UserGameDTO> unfiltered = libraryService.getGames(USER_ID, null, List.of(), null, null, null);

        assertThat(pc).extracting(UserGameDTO::getId).containsExactly(1L, 3L);
        assertThat(pcOrSwitch).extracting(UserGameDTO::getId).containsExactly(1L, 2L, 3L);
        assertThat(none).isEmpty();
        assertThat(unfiltered).hasSize(3);
    }

    private UserGame buildGameWithRating(Long id, GameStatus status, Integer rating) {
        return UserGame.builder()
                .id(id)
                .userId(USER_ID)
                .igdbGameId(id.intValue())
                .gameName("Game " + id)
                .status(status)
                .platforms(new ArrayList<>(List.of("PC")))
                .rating(rating)
                .dateAdded(LocalDateTime.now().minusDays(10))
                .build();
    }

    private UserGame buildGameWithGenresAndPlatform(Long id, List<String> genres, String platform) {
        return UserGame.builder()
                .id(id)
                .userId(USER_ID)
                .igdbGameId(id.intValue())
                .gameName("Game " + id)
                .status(GameStatus.BACKLOG)
                .platforms(new ArrayList<>(List.of(platform)))
                .genres(new ArrayList<>(genres))
                .dateAdded(LocalDateTime.now().minusDays(10))
                .build();
    }
}
