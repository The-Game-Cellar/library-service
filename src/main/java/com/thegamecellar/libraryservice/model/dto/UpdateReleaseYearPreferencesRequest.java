package com.thegamecellar.libraryservice.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Replace-all body for PUT /api/v1/library/release-year-preferences. Empty list clears all. */
@Data
public class UpdateReleaseYearPreferencesRequest {
    @NotNull
    private List<@NotNull @Size(min = 1, max = 50) String> buckets;
}
