package com.thegamecellar.libraryservice.model.dto;

/**
 * One release-era bucket the user marked as a preference. Output of
 * GET /api/v1/library/release-year-preferences. Single field today; record shape leaves room
 * for createdAt / weight / source metadata later without breaking the wire format.
 */
public record UserReleaseYearPreferenceDTO(String bucketLabel) {}
