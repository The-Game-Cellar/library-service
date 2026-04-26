package com.thegamecellar.libraryservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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

    public record GameInfo(String name, String backgroundImage, List<String> genres) {}

    public GameInfo getGameInfo(Integer igdbGameId, String bearerToken) {
        try {
            String url = gameServiceUrl + "/api/v1/games/" + igdbGameId;
            Map<String, Object> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    buildRequest(bearerToken),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();

            if (response == null) return new GameInfo(null, null, Collections.emptyList());

            String name = response.get("name") instanceof String s ? s : null;
            String backgroundImage = response.get("backgroundImage") instanceof String s ? s : null;

            List<String> genres = Collections.emptyList();
            if (response.get("genres") instanceof List<?> genreList) {
                genres = genreList.stream().map(Object::toString).toList();
            }

            return new GameInfo(name, backgroundImage, genres);
        } catch (RestClientException e) {
            log.warn("Failed to fetch game info for game {}: {}", igdbGameId, e.getMessage());
            return new GameInfo(null, null, Collections.emptyList());
        }
    }

    private HttpEntity<Void> buildRequest(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        return new HttpEntity<>(headers);
    }
}
