package com.thegamecellar.libraryservice.model.dto;

/**
 * One genre the user marked as a preference. Output of GET /api/v1/library/genre-preferences.
 * Single field today; record shape leaves room for createdAt / weight / source metadata later
 * without breaking the wire format.
 */
public record UserGenrePreferenceDTO(String genreName) {}
