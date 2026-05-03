package com.thegamecellar.libraryservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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

    public record GameInfo(
            String name,
            String backgroundImage,
            List<String> genres,
            List<String> themes,
            List<String> tags
    ) {}

    public GameInfo getGameInfo(Integer igdbGameId, String bearerToken) {
        try {
            String url = gameServiceUrl + "/api/v1/games/" + igdbGameId;
            Map<String, Object> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    buildRequest(bearerToken),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();

            if (response == null) return emptyInfo();

            String name = response.get("name") instanceof String s ? s : null;
            String backgroundImage = response.get("backgroundImage") instanceof String s ? s : null;

            return new GameInfo(
                    name,
                    backgroundImage,
                    asStringList(response.get("genres")),
                    asStringList(response.get("themes")),
                    asStringList(response.get("tags"))
            );
        } catch (RestClientException e) {
            log.warn("Failed to fetch game info for game {}: {}", igdbGameId, e.getMessage());
            return emptyInfo();
        }
    }

    private static List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }

    private static GameInfo emptyInfo() {
        return new GameInfo(null, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private HttpEntity<Void> buildRequest(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            headers.set("X-Request-ID", requestId);
        }
        return new HttpEntity<>(headers);
    }
}
