package com.thegamecellar.libraryservice.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

// Replace-all body; empty list clears all.
@Data
public class UpdateTagPreferencesRequest {
    @NotNull
    private List<@NotNull @Size(min = 1, max = 100) String> tags;
}
