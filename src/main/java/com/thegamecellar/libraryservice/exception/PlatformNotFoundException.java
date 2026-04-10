package com.thegamecellar.libraryservice.exception;

public class PlatformNotFoundException extends RuntimeException {
    public PlatformNotFoundException(Long platformId) {
        super("Platform not found: " + platformId);
    }
}