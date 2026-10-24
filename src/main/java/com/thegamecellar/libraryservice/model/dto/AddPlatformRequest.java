package com.thegamecellar.libraryservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddPlatformRequest {
    @NotBlank @Size(max = 255)
    private String platformName;
    @NotNull
    private Boolean isPrimary;
}