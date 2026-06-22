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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private final UserGameRepository userGameRepository;
    private final GameServiceClient gameServiceClient;
    private final LibraryWritePublisher writePublisher;

    @Transactional
    public List<UserGameDTO> getGames(String userId, GameStatus status, String platform, String search, String genre, String bearerToken) {
        String searchPattern = (search != null && !search.isBlank())
                ? "%" + search.toLowerCase() + "%"
                : null;
        String genreKey = (genre != null && !genre.isBlank())
                ? genre.toLowerCase()
                : null;
        List<UserGame> games = userGameRepository.findByUserIdWithFilters(userId, status, platform, searchPattern, genreKey);
        return games.stream()
                .map(g -> healStaleMetadata(g, bearerToken))
                .map(this::toDTO)
                .toList();
    }

    // metadata_synced_at == null means the row was added before Game Service responded (or before
    // sync ran). One marker covers genres + themes + tags + released as a unit since they are
    // always written together.
    private UserGame healStaleMetadata(UserGame game, String bearerToken) {
        if (bearerToken == null) return game;
        if (game.getMetadataSyncedAt() != null) return game;

        GameServiceClient.GameInfo info = gameServiceClient.getGameInfo(game.getIgdbGameId(), bearerToken);
        if (info.name() == null && info.genres().isEmpty() && info.themes().isEmpty() && info.tags().isEmpty()) {
            return game;
        }
        game.setGenres(new ArrayList<>(info.genres()));
        game.setThemes(new ArrayList<>(info.themes()));
        game.setTags(new ArrayList<>(info.tags()));
        game.setReleased(info.released() == null ? "" : info.released());
        game.setMetadataSyncedAt(LocalDateTime.now());
        return userGameRepository.save(game);
    }

    public List<String> getGenres(String userId) {
        return userGameRepository.findByUserId(userId).stream()
                .flatMap(g -> g.getGenres().stream())
                .map(String::trim)
                .filter(g -> !g.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> getGamePlatforms(String userId) {
        return userGameRepository.findByUserId(userId).stream()
                .map(UserGame::getPlatform)
                .filter(p -> p != null && !p.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    public List<Integer> getOwnedIgdbGameIds(String userId) {
        return userGameRepository.findIgdbGameIdsByUserId(userId);
    }

    public UserGameDTO getGame(String userId, Long gameId) {
        return userGameRepository.findByIdAndUserId(gameId, userId)
                .map(this::toDTO)
                .orElseThrow(() -> new GameNotFoundException(gameId));
    }

    public UserGameDTO getGameByIgdbId(String userId, Integer igdbGameId) {
        return userGameRepository.findByUserIdAndIgdbGameId(userId, igdbGameId)
                .map(this::toDTO)
                .orElseThrow(() -> GameNotFoundException.forIgdbGameId(igdbGameId));
    }

    @Transactional
    public UserGameDTO addGame(String userId, AddGameRequest request, String bearerToken) {
        if (userGameRepository.existsByUserIdAndIgdbGameId(userId, request.getIgdbGameId())) {
            throw new GameAlreadyInCollectionException(request.getIgdbGameId());
        }
        GameServiceClient.GameInfo gameInfo = gameServiceClient.getGameInfo(request.getIgdbGameId(), bearerToken);
        boolean upstreamResponded = !(gameInfo.name() == null
                && gameInfo.genres().isEmpty()
                && gameInfo.themes().isEmpty()
                && gameInfo.tags().isEmpty());

        UserGame game = UserGame.builder()
                .userId(userId)
                .igdbGameId(request.getIgdbGameId())
                .gameName(gameInfo.name() != null ? gameInfo.name() : request.getGameName())
                .status(request.getStatus())
                .platform(request.getPlatform())
                .rating(request.getRating())
                .notes(request.getNotes())
                .backgroundImage(gameInfo.backgroundImage())
                .genres(new ArrayList<>(gameInfo.genres()))
                .themes(new ArrayList<>(gameInfo.themes()))
                .tags(new ArrayList<>(gameInfo.tags()))
                .released(gameInfo.released() == null ? "" : gameInfo.released())
                .metadataSyncedAt(upstreamResponded ? LocalDateTime.now() : null)
                .build();
        UserGameDTO saved = toDTO(userGameRepository.save(game));
        writePublisher.publish(userId);
        return saved;
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

        UserGameDTO saved = toDTO(userGameRepository.save(game));
        // Profile-affecting fields (status, rating, platform) all trigger an invalidate. The
        // alternative of diffing first to skip pure-notes updates is not worth the complexity;
        // rec-service dedupes via compute_queue upsert anyway.
        writePublisher.publish(userId);
        return saved;
    }

    @Transactional
    public void removeGame(String userId, Long gameId) {
        UserGame game = userGameRepository.findByIdAndUserId(gameId, userId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
        userGameRepository.delete(game);
        writePublisher.publish(userId);
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
                .flatMap(g -> g.getGenres().stream())
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
                .genres(List.copyOf(game.getGenres()))
                .themes(List.copyOf(game.getThemes()))
                .tags(List.copyOf(game.getTags()))
                .released(game.getReleased())
                .status(game.getStatus())
                .rating(game.getRating())
                .platform(game.getPlatform())
                .dateAdded(game.getDateAdded())
                .lastPlayed(game.getLastPlayed())
                .playtime(game.getPlaytime())
                .notes(game.getNotes())
                .build();
    }
}
