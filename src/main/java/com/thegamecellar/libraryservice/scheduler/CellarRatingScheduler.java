package com.thegamecellar.libraryservice.scheduler;

import com.thegamecellar.libraryservice.client.InternalGameClient;
import com.thegamecellar.libraryservice.repository.UserGameRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Publishes what this site's members rated each game to game-service, which blends it
 * against the IGDB score. Aggregates only: no user id ever leaves this service.
 */
@Component
@RequiredArgsConstructor
public class CellarRatingScheduler {

    private static final Logger log = LoggerFactory.getLogger(CellarRatingScheduler.class);
    private static final int BATCH_SIZE = 500;

    private final UserGameRepository userGameRepository;
    private final InternalGameClient internalGameClient;

    @Scheduled(cron = "${cellar.ratings.cron:0 0 2 * * *}")
    public void publishCellarRatings() {
        String runId = UUID.randomUUID().toString();
        try {
            List<InternalGameClient.RatingAggregate> aggregates = readAggregates();

            boolean complete = true;
            int sent = 0;
            for (int from = 0; from < aggregates.size(); from += BATCH_SIZE) {
                List<InternalGameClient.RatingAggregate> batch =
                        aggregates.subList(from, Math.min(from + BATCH_SIZE, aggregates.size()));
                if (internalGameClient.postRatings(runId, batch)) {
                    sent += batch.size();
                } else {
                    complete = false;
                }
            }

            // Pruning on a partial run would clear exactly the games the failed batch was
            // carrying, so a run that lost a batch leaves the previous night's values standing.
            if (!complete) {
                log.error("Cellar rating pass {} incomplete: {} of {} sent, prune skipped",
                        runId, sent, aggregates.size());
                return;
            }
            if (!internalGameClient.pruneRatings(runId)) {
                log.error("Cellar rating pass {} sent {} but could not prune", runId, sent);
                return;
            }
            log.info("Cellar rating pass {} complete: {} games published", runId, sent);
        } catch (Exception e) {
            log.error("Cellar rating pass {} failed, will retry at the next scheduled run", runId, e);
        }
    }

    private List<InternalGameClient.RatingAggregate> readAggregates() {
        List<Object[]> rows = userGameRepository.aggregateRatingsByGame();
        List<InternalGameClient.RatingAggregate> aggregates = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            BigDecimal average = toBigDecimal(row[1]);
            if (average == null) {
                continue;
            }
            aggregates.add(new InternalGameClient.RatingAggregate(
                    (Integer) row[0],
                    average.setScale(2, RoundingMode.HALF_UP),
                    ((Number) row[2]).intValue()));
        }
        return aggregates;
    }

    // AVG comes back as Double on some dialects and BigDecimal on others.
    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal decimal ? decimal : BigDecimal.valueOf(((Number) value).doubleValue());
    }
}
