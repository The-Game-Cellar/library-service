package com.thegamecellar.libraryservice.repository;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.entity.UserGenrePreference;
import com.thegamecellar.libraryservice.model.entity.UserOnboarding;
import com.thegamecellar.libraryservice.model.entity.UserReleaseYearPreference;
import com.thegamecellar.libraryservice.model.entity.UserTagPreference;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.support.LibraryServiceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The library repositories' @Query methods against Postgres with the schema the migrations build.
class LibraryRepositoryPostgresTest extends LibraryServiceTestBase {

    @Autowired private UserOnboardingRepository onboarding;
    @Autowired private UserGenrePreferenceRepository genrePreferences;
    @Autowired private UserTagPreferenceRepository tagPreferences;
    @Autowired private UserReleaseYearPreferenceRepository releaseYearPreferences;
    @Autowired private TransactionTemplate tx;

    private UserGame rated(String userId, int igdbId, String rating) {
        return games.save(UserGame.builder().userId(userId).igdbGameId(igdbId).gameName("Game " + igdbId)
                .status(GameStatus.COMPLETED).rating(rating == null ? null : new BigDecimal(rating))
                .platforms(new ArrayList<>(List.of("PC"))).build());
    }

    @Test
    void findIgdbGameIdsByUserId_listsOnlyThatUsersGames() {
        rated(ALICE, 1001, null);
        rated(ALICE, 1002, null);
        rated(BOB, 1003, null);

        assertThat(games.findIgdbGameIdsByUserId(ALICE)).containsExactlyInAnyOrder(1001, 1002);
        assertThat(games.findIgdbGameIdsByUserId("nobody")).isEmpty();
    }

    // Unrated rows are left out rather than counted as zero, so the average is over people who rated.
    @Test
    void aggregateRatingsByGame_averagesAndCountsTheRatedRowsPerGameInIdOrder() {
        rated(ALICE, 1001, "9.5");
        rated(BOB, 1001, "6.5");
        rated("33333333-0000-4000-8000-000000000003", 1001, null);
        rated(ALICE, 1002, "7");
        rated(BOB, 1003, null);

        List<Object[]> rows = games.aggregateRatingsByGame();

        assertThat(rows).extracting(r -> r[0]).containsExactly(1001, 1002);
        assertThat(new BigDecimal(rows.get(0)[1].toString())).isEqualByComparingTo("8.0");
        assertThat(((Number) rows.get(0)[2]).intValue()).isEqualTo(2);
        assertThat(new BigDecimal(rows.get(1)[1].toString())).isEqualByComparingTo("7.0");
        assertThat(((Number) rows.get(1)[2]).intValue()).isEqualTo(1);
    }

    @Test
    void userOnboardingDeleteByUserId_removesThatUsersRowAndCountsIt() {
        onboarding.save(UserOnboarding.builder().userId(ALICE).build());
        onboarding.save(UserOnboarding.builder().userId(BOB).build());

        Integer removed = tx.execute(s -> onboarding.deleteByUserId(ALICE));
        Integer removedAgain = tx.execute(s -> onboarding.deleteByUserId(ALICE));

        assertThat(removed).isEqualTo(1);
        assertThat(removedAgain).isZero();
        assertThat(onboarding.findById(BOB)).isPresent();
    }

    @Test
    void findByUserIdWithFilters_appliesEachFilterOnlyWhenGiven() {
        games.save(UserGame.builder().userId(ALICE).igdbGameId(1).gameName("Hollow Knight").status(GameStatus.BACKLOG)
                .genres(new ArrayList<>(List.of("Platform", "Adventure"))).platforms(new ArrayList<>(List.of("PC"))).build());
        games.save(UserGame.builder().userId(ALICE).igdbGameId(2).gameName("Celeste").status(GameStatus.COMPLETED)
                .genres(new ArrayList<>(List.of("Platform"))).platforms(new ArrayList<>(List.of("PC"))).build());
        games.save(UserGame.builder().userId(BOB).igdbGameId(3).gameName("Hollow Knight").status(GameStatus.BACKLOG)
                .genres(new ArrayList<>(List.of("Platform"))).platforms(new ArrayList<>(List.of("PC"))).build());

        assertThat(ids(games.findByUserIdWithFilters(ALICE, null, null, null))).containsExactlyInAnyOrder(1, 2);
        assertThat(ids(games.findByUserIdWithFilters(ALICE, GameStatus.COMPLETED, null, null))).containsExactly(2);
        assertThat(ids(games.findByUserIdWithFilters(ALICE, null, "%hollow%", null))).containsExactly(1);
        assertThat(ids(games.findByUserIdWithFilters(ALICE, null, null, "adventure"))).containsExactly(1);
        assertThat(ids(games.findByUserIdWithFilters(ALICE, null, null, "platform"))).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void findAllEligibleForDusty_takesBacklogAndPlayingRowsUnchangedSinceTheThreshold() {
        long backlog = rowWithStatus(1, GameStatus.BACKLOG);
        long playing = rowWithStatus(2, GameStatus.PLAYING);
        long completed = rowWithStatus(3, GameStatus.COMPLETED);
        rowWithStatus(4, GameStatus.BACKLOG);
        Timestamp old = Timestamp.valueOf(LocalDateTime.now().minusDays(100));
        jdbc.update("UPDATE user_games SET status_changed_at = ? WHERE id IN (?, ?, ?)", old, backlog, playing, completed);

        List<UserGame> eligible = games.findAllEligibleForDusty(LocalDateTime.now().minusDays(90));

        assertThat(eligible).extracting(UserGame::getId).containsExactlyInAnyOrder(backlog, playing);
    }

    // The flush + clear on the delete is what lets a replace-all re-insert an overlapping value.
    @Test
    void preferenceDeleteByUserId_removesOnlyThatUsersRowsForEachList() {
        genrePreferences.save(UserGenrePreference.builder().userId(ALICE).genreName("Shooter").build());
        genrePreferences.save(UserGenrePreference.builder().userId(BOB).genreName("Shooter").build());
        tagPreferences.save(UserTagPreference.builder().userId(ALICE).tagName("cozy").build());
        releaseYearPreferences.save(UserReleaseYearPreference.builder().userId(ALICE).bucketLabel("2010s").build());

        Integer genres = tx.execute(s -> genrePreferences.deleteByUserId(ALICE));
        Integer tags = tx.execute(s -> tagPreferences.deleteByUserId(ALICE));
        Integer years = tx.execute(s -> releaseYearPreferences.deleteByUserId(ALICE));

        assertThat(genres).isEqualTo(1);
        assertThat(tags).isEqualTo(1);
        assertThat(years).isEqualTo(1);
        assertThat(genrePreferences.findByUserId(BOB)).hasSize(1);

        tx.executeWithoutResult(s -> {
            genrePreferences.save(UserGenrePreference.builder().userId(BOB).genreName("Puzzle").build());
            genrePreferences.deleteByUserId(BOB);
            genrePreferences.save(UserGenrePreference.builder().userId(BOB).genreName("Shooter").build());
        });
        assertThat(genrePreferences.findByUserId(BOB)).extracting(UserGenrePreference::getGenreName).containsExactly("Shooter");
    }

    private long rowWithStatus(int igdbId, GameStatus status) {
        return games.save(UserGame.builder().userId(ALICE).igdbGameId(igdbId).gameName("G" + igdbId).status(status)
                .platforms(new ArrayList<>(List.of("PC"))).build()).getId();
    }

    private static List<Integer> ids(List<UserGame> list) {
        return list.stream().map(UserGame::getIgdbGameId).toList();
    }
}
