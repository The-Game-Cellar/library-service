package com.thegamecellar.libraryservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    private GameServiceClient gameServiceClient;

    @BeforeEach
    void setUp() {
        gameServiceClient = new GameServiceClient(restTemplate);
        ReflectionTestUtils.setField(gameServiceClient, "gameServiceUrl", "http://localhost:8081");
    }

    @Test
    void shouldReturnGenresForGame() {
        Map<String, Object> responseBody = Map.of(
                "genres", List.of("RPG", "Action"),
                "tags", List.of("Open World", "Story Rich")
        );
        when(restTemplate.exchange(
                eq("http://localhost:8081/api/v1/games/3328"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(responseBody));

        List<String> genres = gameServiceClient.getGenresForGame(3328);

        assertThat(genres).containsExactlyInAnyOrder("RPG", "Action");
    }

    @Test
    void shouldReturnEmptyListOnHttpError() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("Connection refused"));

        List<String> genres = gameServiceClient.getGenresForGame(3328);

        assertThat(genres).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenResponseBodyIsNull() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(null));

        List<String> genres = gameServiceClient.getGenresForGame(3328);

        assertThat(genres).isEmpty();
    }
}
