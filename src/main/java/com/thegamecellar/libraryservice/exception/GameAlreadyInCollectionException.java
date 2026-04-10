package com.thegamecellar.libraryservice.exception;

public class GameAlreadyInCollectionException extends RuntimeException {
    public GameAlreadyInCollectionException(Integer rawgGameId) {
        super("Game already in collection: " + rawgGameId);
    }
}