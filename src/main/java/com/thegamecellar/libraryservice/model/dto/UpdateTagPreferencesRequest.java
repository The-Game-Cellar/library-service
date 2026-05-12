package com.thegamecellar.libraryservice.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Replace-all body for PUT /api/v1/library/tag-preferences. Empty list clears all. */
@Data
public class UpdateTagPreferencesRequest {
    @NotNull
    private List<@NotNull @Size(min = 1, max = 100) String> tags;
}
