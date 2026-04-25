package com.thegamecellar.libraryservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceClient {

    private final RestTemplate restTemplate;

    @Value("${game-service.url}")
    private String gameServiceUrl;

    public record GameInfo(String backgroundImage, List<String> genres) {}

    public GameInfo getGameInfo(Integer igdbGameId) {
        try {
            String url = gameServiceUrl + "/api/v1/games/" + igdbGameId;
            Map<String, Object> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();

            if (response == null) return new GameInfo(null, Collections.emptyList());

            String backgroundImage = response.get("backgroundImage") instanceof String s ? s : null;

            List<String> genres = Collections.emptyList();
            if (response.get("genres") instanceof List<?> genreList) {
                genres = genreList.stream().map(Object::toString).toList();
            }

            return new GameInfo(backgroundImage, genres);
        } catch (RestClientException e) {
            log.warn("Failed to fetch game info for game {}: {}", igdbGameId, e.getMessage());
            return new GameInfo(null, Collections.emptyList());
        }
    }
}
