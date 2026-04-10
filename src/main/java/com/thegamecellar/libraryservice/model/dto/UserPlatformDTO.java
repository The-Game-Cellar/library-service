package com.thegamecellar.libraryservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPlatformDTO {
    private Long id;
    private String platformName;
    private Boolean isPrimary;
}