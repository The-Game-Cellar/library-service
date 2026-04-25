package com.thegamecellar.libraryservice.exception;

public class GameAlreadyInCollectionException extends RuntimeException {
    public GameAlreadyInCollectionException(Integer igdbGameId) {
        super("Game already in collection: " + igdbGameId);
    }
}