package com.thegamecellar.libraryservice.model.dto;

import java.time.LocalDateTime;

public record AccountDeletionDTO(String userId, LocalDateTime requestedAt) {}
