package com.thegamecellar.libraryservice.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetPrimaryRequest {
    @NotNull
    private Boolean isPrimary;
}
