package com.thegamecellar.libraryservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddPlatformRequest {
    @NotBlank
    private String platformName;
    @NotNull
    private Boolean isPrimary;
}