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
    void shouldReturnGameInfoWithNameGenresAndImage() {
        Map<String, Object> responseBody = Map.of(
                "name", "The Witcher 3",
                "backgroundImage", "https://example.com/witcher.jpg",
                "genres", List.of("RPG", "Action"),
                "themes", List.of("Fantasy", "Historical"),
                "tags", List.of("open world", "story rich")
        );
        when(restTemplate.exchange(
                eq("http://localhost:8081/api/v1/games/3328"),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(responseBody));

        GameServiceClient.GameInfo info = gameServiceClient.getGameInfo(3328, "Bearer test-token");

        assertThat(info.name()).isEqualTo("The Witcher 3");
        assertThat(info.backgroundImage()).isEqualTo("https://example.com/witcher.jpg");
        assertThat(info.genres()).containsExactlyInAnyOrder("RPG", "Action");
        assertThat(info.themes()).containsExactlyInAnyOrder("Fantasy", "Historical");
        assertThat(info.tags()).containsExactlyInAnyOrder("open world", "story rich");
    }

    @Test
    void shouldReturnEmptyListsWhenThemesAndTagsAbsent() {
        Map<String, Object> responseBody = Map.of(
                "name", "Old Cached Game",
                "genres", List.of("RPG")
        );
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(responseBody));

        GameServiceClient.GameInfo info = gameServiceClient.getGameInfo(3328, "Bearer test-token");

        assertThat(info.themes()).isEmpty();
        assertThat(info.tags()).isEmpty();
    }

    @Test
    void shouldReturnEmptyGameInfoOnHttpError() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("Connection refused"));

        GameServiceClient.GameInfo info = gameServiceClient.getGameInfo(3328, "Bearer test-token");

        assertThat(info.name()).isNull();
        assertThat(info.backgroundImage()).isNull();
        assertThat(info.genres()).isEmpty();
        assertThat(info.themes()).isEmpty();
        assertThat(info.tags()).isEmpty();
    }

    @Test
    void shouldReturnEmptyGameInfoWhenResponseBodyIsNull() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(null));

        GameServiceClient.GameInfo info = gameServiceClient.getGameInfo(3328, "Bearer test-token");

        assertThat(info.name()).isNull();
        assertThat(info.backgroundImage()).isNull();
        assertThat(info.genres()).isEmpty();
        assertThat(info.themes()).isEmpty();
        assertThat(info.tags()).isEmpty();
    }
}
