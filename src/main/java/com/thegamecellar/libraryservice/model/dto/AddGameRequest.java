package com.thegamecellar.libraryservice.model.dto;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import lombok.Data;

@Data
public class AddGameRequest {
    private Integer rawgGameId;
    private String gameName;
    private GameStatus status;
    private String platform;
    private Integer rating;
    private String notes;
}