package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.exception.GameAlreadyInCollectionException;
import com.thegamecellar.libraryservice.exception.GameNotFoundException;
import com.thegamecellar.libraryservice.model.dto.*;
import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private final UserGameRepository userGameRepository;
    private final GameServiceClient gameServiceClient;

    @Transactional
    public List<UserGameDTO> getGames(String userId, GameStatus status, String platform, String search, String genre, String bearerToken) {
        String searchPattern = (search != null && !search.isBlank())
                ? "%" + search.toLowerCase() + "%"
                : null;
        String genrePattern = (genre != null && !genre.isBlank())
                ? "%" + genre.toLowerCase() + "%"
                : null;
        List<UserGame> games = userGameRepository.findByUserIdWithFilters(userId, status, platform, searchPattern, genrePattern);
        return games.stream()
                .map(g -> healStaleMetadata(g, bearerToken))
                .map(this::toDTO)
                .toList();
    }

    /**
     * Lazy backfill for rows whose genres / themes / tags columns are still NULL.
     * Mirrors Game Service's stale-cache pattern. Sentinel: empty string "" marks "synced, none"
     * so rows aren't re-checked forever. NULL = never synced.
     */
    private UserGame healStaleMetadata(UserGame game, String bearerToken) {
        if (bearerToken == null) return game;
        if (game.getThemes() != null && game.getTags() != null && game.getGenres() != null) return game;

        GameServiceClient.GameInfo info = gameServiceClient.getGameInfo(game.getIgdbGameId(), bearerToken);
        if (info.name() == null && info.genres().isEmpty() && info.themes().isEmpty() && info.tags().isEmpty()) {
            return game;
        }
        if (game.getGenres() == null) game.setGenres(joinOrEmpty(info.genres()));
        if (game.getThemes() == null) game.setThemes(joinOrEmpty(info.themes()));
        if (game.getTags() == null) game.setTags(joinOrEmpty(info.tags()));
        return userGameRepository.save(game);
    }

    private static String joinOrEmpty(List<String> values) {
        return (values == null || values.isEmpty()) ? "" : String.join(",", values);
    }

    public List<String> getGenres(String userId) {
        return userGameRepository.findByUserId(userId).stream()
                .map(UserGame::getGenres)
                .filter(g -> g != null && !g.isBlank())
                .flatMap(g -> java.util.Arrays.stream(g.split(",")))
                .map(String::trim)
                .filter(g -> !g.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    /** Slim id-only view for excluding owned games from external surfaces (e.g. Coming Soon). */
    public List<Integer> getOwnedIgdbGameIds(String userId) {
        return userGameRepository.findIgdbGameIdsByUserId(userId);
    }

    public UserGameDTO getGame(String userId, Long gameId) {
        return userGameRepository.findByIdAndUserId(gameId, userId)
                .map(this::toDTO)
                .orElseThrow(() -> new GameNotFoundException(gameId));
    }

    @Transactional
    public UserGameDTO addGame(String userId, AddGameRequest request, String bearerToken) {
        if (userGameRepository.existsByUserIdAndIgdbGameId(userId, request.getIgdbGameId())) {
            throw new GameAlreadyInCollectionException(request.getIgdbGameId());
        }
        GameServiceClient.GameInfo gameInfo = gameServiceClient.getGameInfo(request.getIgdbGameId(), bearerToken);

        UserGame game = UserGame.builder()
                .userId(userId)
                .igdbGameId(request.getIgdbGameId())
                .gameName(gameInfo.name() != null ? gameInfo.name() : request.getGameName())
                .status(request.getStatus())
                .platform(request.getPlatform())
                .rating(request.getRating())
                .notes(request.getNotes())
                .backgroundImage(gameInfo.backgroundImage())
                .genres(joinOrEmpty(gameInfo.genres()))
                .themes(joinOrEmpty(gameInfo.themes()))
                .tags(joinOrEmpty(gameInfo.tags()))
                .build();
        return toDTO(userGameRepository.save(game));
    }

    @Transactional
    public UserGameDTO updateGame(String userId, Long gameId, UpdateGameRequest request) {
        UserGame game = userGameRepository.findByIdAndUserId(gameId, userId)
                .orElseThrow(() -> new GameNotFoundException(gameId));

        if (request.getStatus() != null) {
            if (request.getStatus() == GameStatus.DUSTY) {
                throw new IllegalArgumentException("DUSTY status is auto-assigned and cannot be set manually");
            }
            game.setStatus(request.getStatus());
            if (request.getStatus() == GameStatus.PLAYING) {
                game.setLastPlayed(LocalDateTime.now());
            }
        }
        if (request.getRating() != null) game.setRating(request.getRating());
        if (request.getPlatform() != null) game.setPlatform(request.getPlatform());
        if (request.getLastPlayed() != null) game.setLastPlayed(request.getLastPlayed());
        if (request.getPlaytime() != null) game.setPlaytime(request.getPlaytime());
        if (request.getNotes() != null) game.setNotes(request.getNotes());

        return toDTO(userGameRepository.save(game));
    }

    @Transactional
    public void removeGame(String userId, Long gameId) {
        UserGame game = userGameRepository.findByIdAndUserId(gameId, userId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
        userGameRepository.delete(game);
    }

    public List<UserGameDTO> getByStatus(String userId, GameStatus status) {
        return userGameRepository.findByUserIdAndStatus(userId, status)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<UserGameDTO> getDustyGames(String userId) {
        return userGameRepository.findByUserIdAndStatus(userId, GameStatus.DUSTY)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public UserStatsDTO getStats(String userId) {
        List<UserGame> games = userGameRepository.findByUserId(userId);

        Map<GameStatus, Long> byStatus = games.stream()
                .collect(Collectors.groupingBy(UserGame::getStatus, Collectors.counting()));

        List<Integer> ratings = games.stream()
                .map(UserGame::getRating)
                .filter(r -> r != null)
                .toList();

        Double averageRating = ratings.isEmpty() ? null :
                ratings.stream().mapToInt(Integer::intValue).average().orElse(0);

        Map<String, Long> byGenre = games.stream()
                .map(UserGame::getGenres)
                .filter(g -> g != null && !g.isBlank())
                .flatMap(g -> java.util.Arrays.stream(g.split(",")))
                .map(String::trim)
                .filter(g -> !g.isBlank())
                .collect(Collectors.groupingBy(g -> g, Collectors.counting()));

        Map<String, Long> byPlatform = games.stream()
                .map(UserGame::getPlatform)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.groupingBy(p -> p, Collectors.counting()));

        return UserStatsDTO.builder()
                .totalGames(games.size())
                .byStatus(byStatus)
                .averageRating(averageRating)
                .totalRated(ratings.size())
                .byGenre(byGenre)
                .byPlatform(byPlatform)
                .build();
    }

    private UserGameDTO toDTO(UserGame game) {
        return UserGameDTO.builder()
                .id(game.getId())
                .igdbGameId(game.getIgdbGameId())
                .gameName(game.getGameName())
                .backgroundImage(game.getBackgroundImage())
                .genres(splitCsv(game.getGenres()))
                .themes(splitCsv(game.getThemes()))
                .tags(splitCsv(game.getTags()))
                .status(game.getStatus())
                .rating(game.getRating())
                .platform(game.getPlatform())
                .dateAdded(game.getDateAdded())
                .lastPlayed(game.getLastPlayed())
                .playtime(game.getPlaytime())
                .notes(game.getNotes())
                .build();
    }

    private static List<String> splitCsv(String csv) {
        return (csv != null && !csv.isBlank())
                ? List.of(csv.split(","))
                : List.of();
    }
}