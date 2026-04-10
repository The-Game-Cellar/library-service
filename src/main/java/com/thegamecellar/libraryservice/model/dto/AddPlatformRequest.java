package com.thegamecellar.libraryservice.model.dto;

import lombok.Data;

@Data
public class AddPlatformRequest {
    private String platformName;
    private Boolean isPrimary;
}