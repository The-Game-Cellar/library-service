package com.thegamecellar.libraryservice.exception;

public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(Long gameId) {
        super("Game not found in collection: " + gameId);
    }

    public static GameNotFoundException forIgdbGameId(Integer igdbGameId) {
        return new GameNotFoundException("Game not found in collection for igdbGameId: " + igdbGameId);
    }

    private GameNotFoundException(String message) {
        super(message);
    }
}