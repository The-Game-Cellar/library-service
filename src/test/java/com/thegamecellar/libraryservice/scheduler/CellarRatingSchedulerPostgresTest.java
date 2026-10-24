package com.thegamecellar.libraryservice.scheduler;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.support.LibraryServiceTestBase;
import com.thegamecellar.libraryservice.support.StubHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// One nightly pass against Postgres, with game-service a stub at its URL: the aggregates go out in
// batches under one run id with the shared internal token, then the prune closes the run.
class CellarRatingSchedulerPostgresTest extends LibraryServiceTestBase {

    @Autowired private CellarRatingScheduler scheduler;

    private void rated(String userId, int igdbId, String rating) {
        games.save(UserGame.builder().userId(userId).igdbGameId(igdbId).gameName("Game " + igdbId)
                .status(GameStatus.COMPLETED).rating(rating == null ? null : new BigDecimal(rating))
                .platforms(new ArrayList<>(List.of("PC"))).build());
    }

    private static void gameServiceAccepts() {
        GAME_SERVICE.on("POST", "/internal/games/ratings", StubHttpServer.StubResponse.json(200, "{\"stored\":1}"));
    }

    // Aggregates only: what a member rated never leaves the service, only the average and the count.
    @Test
    void aPassPostsTheAggregatesUnderOneRunIdAndThenPrunesThatRun() {
        rated(ALICE, 1001, "9.5");
        rated(BOB, 1001, "6.5");
        rated(ALICE, 1002, "7");
        rated(BOB, 1003, null);
        gameServiceAccepts();

        scheduler.publishCellarRatings();

        List<StubHttpServer.RecordedRequest> posted = GAME_SERVICE.recorded("POST", "/internal/games/ratings");
        assertThat(posted).hasSize(2);
        StubHttpServer.RecordedRequest batch = posted.get(0);
        StubHttpServer.RecordedRequest prune = posted.get(1);
        assertThat(batch.path()).isEqualTo("/internal/games/ratings");
        assertThat(prune.path()).isEqualTo("/internal/games/ratings/prune");
        assertThat(batch.query()).startsWith("runId=").isEqualTo(prune.query());
        assertThat(batch.header("X-Internal-Token")).isEqualTo(INTERNAL_TOKEN);
        assertThat(prune.header("X-Internal-Token")).isEqualTo(INTERNAL_TOKEN);
        assertThat(batch.body()).isEqualTo(
                "[{\"igdbGameId\":1001,\"average\":8.00,\"count\":2},{\"igdbGameId\":1002,\"average\":7.00,\"count\":1}]");
        assertThat(batch.body()).doesNotContain(ALICE).doesNotContain(BOB);
    }

    @Test
    void moreThanOneBatchOfGamesGoesOutInSeveralPostsUnderTheSameRun() {
        jdbc.batchUpdate("""
                INSERT INTO user_games (user_id, igdb_game_id, game_name, status, rating, status_changed_at)
                VALUES (?, ?, 'G', 'COMPLETED', 8.0, CURRENT_TIMESTAMP)
                """, java.util.stream.IntStream.rangeClosed(1, 501)
                .mapToObj(i -> new Object[]{ALICE, i}).toList());
        gameServiceAccepts();

        scheduler.publishCellarRatings();

        List<StubHttpServer.RecordedRequest> batches = GAME_SERVICE.recorded("POST", "/internal/games/ratings").stream()
                .filter(r -> r.path().equals("/internal/games/ratings")).toList();
        assertThat(batches).hasSize(2);
        assertThat(batches.get(0).query()).isEqualTo(batches.get(1).query());
        assertThat(GAME_SERVICE.recorded("POST", "/internal/games/ratings/prune")).hasSize(1);
    }

    // Pruning after a lost batch would clear exactly the games that batch carried, so the run stops.
    @Test
    void aFailedBatchSkipsThePrune() {
        rated(ALICE, 1001, "8");
        GAME_SERVICE.on("POST", "/internal/games/ratings", StubHttpServer.StubResponse.json(503, "{}"));

        scheduler.publishCellarRatings();

        assertThat(GAME_SERVICE.recorded("POST", "/internal/games/ratings/prune")).isEmpty();
        assertThat(GAME_SERVICE.recorded("POST", "/internal/games/ratings")).hasSize(1);
    }

    @Test
    void withNothingRatedThePassSendsNoBatchAndStillPrunes() {
        rated(ALICE, 1001, null);
        gameServiceAccepts();

        scheduler.publishCellarRatings();

        assertThat(GAME_SERVICE.recorded("POST", "/internal/games/ratings")).extracting(StubHttpServer.RecordedRequest::path)
                .containsExactly("/internal/games/ratings/prune");
    }
}
