package com.thegamecellar.libraryservice.service;

import com.thegamecellar.libraryservice.exception.GameAlreadyInCollectionException;
import com.thegamecellar.libraryservice.exception.GameNotFoundException;
import com.thegamecellar.libraryservice.model.dto.*;
import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private final UserGameRepository userGameRepository;
    private final GameServiceClient gameServiceClient;

    public List<UserGameDTO> getGames(String userId, GameStatus status, String platform, String search, String genre) {
        List<UserGame> games = userGameRepository.findByUserIdWithFilters(userId, status, platform, search);

        if (genre != null && !genre.isBlank()) {
            games = games.stream()
                    .filter(game -> {
                        List<String> genres = gameServiceClient.getGenresForGame(game.getRawgGameId());
                        return genres.stream().anyMatch(g -> g.equalsIgnoreCase(genre));
                    })
                    .toList();
        }

        return games.stream().map(this::toDTO).toList();
    }

    public UserGameDTO getGame(String userId, Long gameId) {
        return userGameRepository.findById(gameId)
                .filter(g -> g.getUserId().equals(userId))
                .map(this::toDTO)
                .orElseThrow(() -> new GameNotFoundException(gameId));
    }

    public UserGameDTO addGame(String userId, AddGameRequest request) {
        if (userGameRepository.existsByUserIdAndRawgGameId(userId, request.getRawgGameId())) {
            throw new GameAlreadyInCollectionException(request.getRawgGameId());
        }
        UserGame game = UserGame.builder()
                .userId(userId)
                .rawgGameId(request.getRawgGameId())
                .gameName(request.getGameName())
                .status(request.getStatus())
                .platform(request.getPlatform())
                .rating(request.getRating())
                .notes(request.getNotes())
                .build();
        return toDTO(userGameRepository.save(game));
    }

    public UserGameDTO updateGame(String userId, Long gameId, UpdateGameRequest request) {
        UserGame game = userGameRepository.findById(gameId)
                .filter(g -> g.getUserId().equals(userId))
                .orElseThrow(() -> new GameNotFoundException(gameId));

        if (request.getStatus() != null) game.setStatus(request.getStatus());
        if (request.getRating() != null) game.setRating(request.getRating());
        if (request.getPlatform() != null) game.setPlatform(request.getPlatform());
        if (request.getLastPlayed() != null) game.setLastPlayed(request.getLastPlayed());
        if (request.getPlaytime() != null) game.setPlaytime(request.getPlaytime());
        if (request.getNotes() != null) game.setNotes(request.getNotes());

        return toDTO(userGameRepository.save(game));
    }

    public void removeGame(String userId, Long gameId) {
        UserGame game = userGameRepository.findById(gameId)
                .filter(g -> g.getUserId().equals(userId))
                .orElseThrow(() -> new GameNotFoundException(gameId));
        userGameRepository.delete(game);
    }

    public List<UserGameDTO> getByStatus(String userId, GameStatus status) {
        return userGameRepository.findByUserIdAndStatus(userId, status)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<UserGameDTO> getForgottenGames(String userId, int days) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        return userGameRepository.findForgottenGames(userId, threshold)
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

        return UserStatsDTO.builder()
                .totalGames(games.size())
                .byStatus(byStatus)
                .averageRating(averageRating)
                .totalRated(ratings.size())
                .build();
    }

    private UserGameDTO toDTO(UserGame game) {
        return UserGameDTO.builder()
                .id(game.getId())
                .rawgGameId(game.getRawgGameId())
                .gameName(game.getGameName())
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