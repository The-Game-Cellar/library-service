package com.thegamecellar.libraryservice.scheduler;

import com.thegamecellar.libraryservice.client.InternalGameClient;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CellarRatingSchedulerTest {

    @Mock
    private UserGameRepository userGameRepository;

    @Mock
    private InternalGameClient internalGameClient;

    @InjectMocks
    private CellarRatingScheduler scheduler;

    private static List<Object[]> rows(int count) {
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Object[]{1000 + i, 8.0d, 2L});
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<List<InternalGameClient.RatingAggregate>> capturedBatches() {
        ArgumentCaptor<List<InternalGameClient.RatingAggregate>> captor = ArgumentCaptor.forClass(List.class);
        verify(internalGameClient, times(3)).postRatings(anyString(), captor.capture());
        return captor.getAllValues();
    }

    @Test
    void sendsEveryAggregateAndThenClosesThePass() {
        when(userGameRepository.aggregateRatingsByGame()).thenReturn(rows(1));
        when(internalGameClient.postRatings(anyString(), any())).thenReturn(true);
        when(internalGameClient.pruneRatings(anyString())).thenReturn(true);

        scheduler.publishCellarRatings();

        ArgumentCaptor<List<InternalGameClient.RatingAggregate>> batch = ArgumentCaptor.forClass(List.class);
        verify(internalGameClient).postRatings(anyString(), batch.capture());
        InternalGameClient.RatingAggregate only = batch.getValue().get(0);
        assertThat(only.igdbGameId()).isEqualTo(1000);
        assertThat(only.average()).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(only.count()).isEqualTo(2);
        verify(internalGameClient).pruneRatings(anyString());
    }

    @Test
    void splitsIntoBatchesOfFiveHundred() {
        when(userGameRepository.aggregateRatingsByGame()).thenReturn(rows(1001));
        when(internalGameClient.postRatings(anyString(), any())).thenReturn(true);
        when(internalGameClient.pruneRatings(anyString())).thenReturn(true);

        scheduler.publishCellarRatings();

        List<List<InternalGameClient.RatingAggregate>> batches = capturedBatches();
        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).hasSize(500);
        assertThat(batches.get(1)).hasSize(500);
        assertThat(batches.get(2)).hasSize(1);
    }

    // Pruning after a lost batch would clear exactly the games that batch was carrying.
    @Test
    void aFailedBatchStopsThePruneSoLastNightsValuesStand() {
        when(userGameRepository.aggregateRatingsByGame()).thenReturn(rows(501));
        when(internalGameClient.postRatings(anyString(), any())).thenReturn(true).thenReturn(false);

        scheduler.publishCellarRatings();

        verify(internalGameClient, never()).pruneRatings(anyString());
    }

    // Every rating deleted is a real state: the prune is what clears the stored averages.
    @Test
    void anEmptyAggregateStillPrunesSoNothingStaleSurvives() {
        when(userGameRepository.aggregateRatingsByGame()).thenReturn(List.of());
        when(internalGameClient.pruneRatings(anyString())).thenReturn(true);

        scheduler.publishCellarRatings();

        verify(internalGameClient, never()).postRatings(anyString(), any());
        verify(internalGameClient).pruneRatings(anyString());
    }

    @Test
    void everyBatchAndThePruneShareOneRunId() {
        when(userGameRepository.aggregateRatingsByGame()).thenReturn(rows(1001));
        when(internalGameClient.postRatings(anyString(), any())).thenReturn(true);
        when(internalGameClient.pruneRatings(anyString())).thenReturn(true);

        scheduler.publishCellarRatings();

        ArgumentCaptor<String> runIds = ArgumentCaptor.forClass(String.class);
        verify(internalGameClient, times(3)).postRatings(runIds.capture(), any());
        String runId = runIds.getAllValues().get(0);
        assertThat(runIds.getAllValues()).containsOnly(runId);
        verify(internalGameClient).pruneRatings(eq(runId));
    }

    @Test
    void aRepositoryFailureIsSwallowedSoTheSchedulerSurvivesToTheNextRun() {
        when(userGameRepository.aggregateRatingsByGame()).thenThrow(new RuntimeException("db down"));

        scheduler.publishCellarRatings();

        verify(internalGameClient, never()).pruneRatings(anyString());
    }
}
