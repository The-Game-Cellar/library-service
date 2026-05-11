package com.thegamecellar.libraryservice.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request body for PUT /api/v1/library/genre-preferences. Replace-all semantics — the supplied
 * list becomes the user's complete preference set; previous rows are deleted in the same
 * transaction. Empty list is valid (clears all preferences).
 */
@Data
public class UpdateGenrePreferencesRequest {
    @NotNull
    private List<@NotNull @Size(min = 1, max = 100) String> genres;
}
