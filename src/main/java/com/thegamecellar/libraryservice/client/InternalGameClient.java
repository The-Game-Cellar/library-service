package com.thegamecellar.libraryservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;

/**
 * The nightly Cellar score pass writes through here. Separate from GameServiceClient,
 * which forwards a user's JWT to the public API: a scheduled job has no user, so it
 * authenticates with the shared internal token instead.
 */
@Slf4j
@Component
public class InternalGameClient {

    public record RatingAggregate(Integer igdbGameId, BigDecimal average, Integer count) {
    }

    private final RestTemplate restTemplate;
    private final String gameServiceUrl;
    private final String internalToken;

    public InternalGameClient(RestTemplate restTemplate,
                              @Value("${game-service.url}") String gameServiceUrl,
                              @Value("${security.internal.token:}") String internalToken) {
        this.restTemplate = restTemplate;
        this.gameServiceUrl = gameServiceUrl;
        this.internalToken = internalToken;
    }

    /** True when the batch was accepted. A failure is logged here and reported to the caller. */
    public boolean postRatings(String runId, List<RatingAggregate> batch) {
        String url = UriComponentsBuilder.fromUriString(gameServiceUrl + "/internal/games/ratings")
                .queryParam("runId", runId)
                .toUriString();
        try {
            restTemplate.exchange(url, HttpMethod.POST, request(batch), Void.class);
            return true;
        } catch (RestClientException ex) {
            log.warn("Cellar ratings batch of {} failed: {}", batch.size(), ex.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Closes the pass: game-service clears every rating row an older run wrote. Only safe
     * after every batch succeeded, which is the caller's decision to make.
     */
    public boolean pruneRatings(String runId) {
        String url = UriComponentsBuilder.fromUriString(gameServiceUrl + "/internal/games/ratings/prune")
                .queryParam("runId", runId)
                .toUriString();
        try {
            restTemplate.exchange(url, HttpMethod.POST, request(null), Void.class);
            return true;
        } catch (RestClientException ex) {
            log.warn("Cellar ratings prune failed: {}", ex.getClass().getSimpleName());
            return false;
        }
    }

    private HttpEntity<List<RatingAggregate>> request(List<RatingAggregate> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalToken != null && !internalToken.isBlank()) {
            headers.set("X-Internal-Token", internalToken);
        }
        return new HttpEntity<>(body, headers);
    }
}
