package com.thegamecellar.libraryservice.exception;

public class PlatformAlreadyAddedException extends RuntimeException {
    public PlatformAlreadyAddedException(String platformName) {
        super("Platform already added: " + platformName);
    }
}