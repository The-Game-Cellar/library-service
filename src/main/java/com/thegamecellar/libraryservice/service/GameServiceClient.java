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

    public List<String> getGenresForGame(Integer rawgGameId) {
        try {
            String url = gameServiceUrl + "/api/v1/games/" + rawgGameId;
            Map<String, Object> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();

            if (response == null) {
                return Collections.emptyList();
            }

            Object genres = response.get("genres");
            if (genres instanceof List<?> genreList) {
                return genreList.stream()
                        .map(Object::toString)
                        .toList();
            }

            return Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("Failed to fetch genres for game {}: {}", rawgGameId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
