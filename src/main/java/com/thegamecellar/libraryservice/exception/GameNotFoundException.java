package com.thegamecellar.libraryservice.exception;

public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(Long gameId) {
        super("Game not found in collection: " + gameId);
    }
}