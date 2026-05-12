package com.thegamecellar.libraryservice.model.dto;

/**
 * One tag the user marked as a preference. Output of GET /api/v1/library/tag-preferences.
 * Single field today; record shape leaves room for createdAt / weight / source metadata later
 * without breaking the wire format.
 */
public record UserTagPreferenceDTO(String tagName) {}
