package com.thegamecellar.libraryservice.integration;

import com.thegamecellar.libraryservice.exception.GameAlreadyInCollectionException;
import com.thegamecellar.libraryservice.exception.GameNotFoundException;
import com.thegamecellar.libraryservice.model.dto.AddGameRequest;
import com.thegamecellar.libraryservice.model.dto.AccountDeletionDTO;
import com.thegamecellar.libraryservice.model.dto.AccountExportDTO;
import com.thegamecellar.libraryservice.model.dto.UpdateGameRequest;
import com.thegamecellar.libraryservice.model.dto.UserGameDTO;
import com.thegamecellar.libraryservice.model.dto.UserGenrePreferenceDTO;
import com.thegamecellar.libraryservice.model.dto.UserReleaseYearPreferenceDTO;
import com.thegamecellar.libraryservice.model.dto.UserTagPreferenceDTO;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.scheduler.DustyTransitionScheduler;
import com.thegamecellar.libraryservice.service.AccountService;
import com.thegamecellar.libraryservice.service.GameServiceClient;
import com.thegamecellar.libraryservice.service.GenrePreferenceService;
import com.thegamecellar.libraryservice.service.LibraryService;
import com.thegamecellar.libraryservice.service.LibraryWritePublisher;
import com.thegamecellar.libraryservice.service.ReleaseYearPreferenceService;
import com.thegamecellar.libraryservice.service.TagPreferenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The service layer against a real Postgres 17 running the real Flyway migrations. The two
// outbound edges are mocked: the game-service client, which is HTTP, and the Redis
// publisher, which is best-effort by design. Everything between them, including the
// unique constraints and the @PrePersist timestamps, is the production code path.
//
// disabledWithoutDocker: skipped rather than failed where no Docker daemon is reachable. On
// Windows with Docker Desktop, Testcontainers may need `%USERPROFILE%\.testcontainers.properties`
// pointing docker.host at the Desktop engine pipe; see the recommendation-service test of the
// same shape.
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://unused"
})
class LibraryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("library_test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String ALICE = "11111111-0000-4000-8000-000000000001";
    private static final String BOB = "22222222-0000-4000-8000-000000000002";
    private static final String TOKEN = "bearer-for-game-service";

    @MockitoBean private GameServiceClient gameServiceClient;
    @MockitoBean private LibraryWritePublisher writePublisher;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private LibraryService libraryService;
    @Autowired private GenrePreferenceService genrePreferenceService;
    @Autowired private TagPreferenceService tagPreferenceService;
    @Autowired private ReleaseYearPreferenceService releaseYearPreferenceService;
    @Autowired private AccountService accountService;
    @Autowired private DustyTransitionScheduler dustyScheduler;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void catalogAnswers() {
        when(gameServiceClient.getGameInfo(eq(1001), any())).thenReturn(new GameServiceClient.GameInfo(
                "Hollow Knight", "hk.jpg", List.of("Platform", "Adventure"), List.of("Action"), List.of("metroidvania"), "2017-02-24"));
        when(gameServiceClient.getGameInfo(eq(1002), any())).thenReturn(new GameServiceClient.GameInfo(
                "Stardew Valley", "sv.jpg", List.of("Simulator", "Role-playing (RPG)"), List.of(), List.of("farming"), "2016-02-26"));
        when(gameServiceClient.getGameInfo(eq(1003), any())).thenReturn(new GameServiceClient.GameInfo(
                "Celeste", "ce.jpg", List.of("Platform"), List.of(), List.of(), "2018-01-25"));
    }

    @AfterEach
    void purgeEveryone() {
        accountService.purgeUser(ALICE);
        accountService.purgeUser(BOB);
    }

    private UserGameDTO add(String userId, int igdbId, GameStatus status) {
        AddGameRequest request = new AddGameRequest();
        request.setIgdbGameId(igdbId);
        request.setGameName("placeholder");
        request.setStatus(status);
        request.setPlatform("PC");
        return libraryService.addGame(userId, request, TOKEN);
    }

    @Test
    void theRealMigrationsAreWhatTheSchemaComesFrom() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class);

        assertThat(applied).isGreaterThanOrEqualTo(8);
    }

    @Test
    void addingAGameCachesTheCatalogMetadataAndScopesItToTheOwner() {
        UserGameDTO saved = add(ALICE, 1001, GameStatus.BACKLOG);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getGameName()).isEqualTo("Hollow Knight");
        assertThat(saved.getGenres()).containsExactly("Platform", "Adventure");
        assertThat(saved.getTags()).containsExactly("metroidvania");
        assertThat(saved.getReleased()).isEqualTo("2017-02-24");
        assertThat(saved.getDateAdded()).isNotNull();

        List<UserGameDTO> alices = libraryService.getGames(ALICE, null, null, null, null, TOKEN);
        assertThat(alices).extracting(UserGameDTO::getIgdbGameId).containsExactly(1001);
        assertThat(libraryService.getGames(BOB, null, null, null, null, TOKEN)).isEmpty();

        // The row belongs to Alice: Bob cannot read it by id, and the UNIQUE (user_id, igdb_game_id)
        // constraint is per owner, so Bob can add the same catalog game.
        assertThatThrownBy(() -> libraryService.getGame(BOB, saved.getId()))
                .isInstanceOf(GameNotFoundException.class);
        assertThat(add(BOB, 1001, GameStatus.WISHLIST).getGameName()).isEqualTo("Hollow Knight");

        verify(writePublisher, times(2)).publish(any());
    }

    @Test
    void addingTheSameGameTwiceIsRefusedBeforeTheCatalogIsAsked() {
        add(ALICE, 1001, GameStatus.BACKLOG);

        assertThatThrownBy(() -> add(ALICE, 1001, GameStatus.PLAYING))
                .isInstanceOf(GameAlreadyInCollectionException.class);

        verify(gameServiceClient, times(1)).getGameInfo(eq(1001), any());
        assertThat(libraryService.getGames(ALICE, null, null, null, null, TOKEN)).hasSize(1);
    }

    @Test
    void filtersRunAgainstTheJoinTablesAndTheStatusColumn() {
        add(ALICE, 1001, GameStatus.BACKLOG);
        add(ALICE, 1002, GameStatus.PLAYING);
        add(ALICE, 1003, GameStatus.COMPLETED);

        assertThat(libraryService.getGames(ALICE, GameStatus.PLAYING, null, null, null, TOKEN))
                .extracting(UserGameDTO::getGameName).containsExactly("Stardew Valley");
        assertThat(libraryService.getGames(ALICE, null, null, null, "platform", TOKEN))
                .extracting(UserGameDTO::getIgdbGameId).containsExactlyInAnyOrder(1001, 1003);
        assertThat(libraryService.getGames(ALICE, null, null, "STARdew", null, TOKEN))
                .extracting(UserGameDTO::getIgdbGameId).containsExactly(1002);
        assertThat(libraryService.getGenres(ALICE))
                .containsExactly("Adventure", "Platform", "Role-playing (RPG)", "Simulator");
    }

    @Test
    void updatingStatusToPlayingStampsLastPlayedAndDustyCannotBeSetByHand() {
        UserGameDTO saved = add(ALICE, 1001, GameStatus.BACKLOG);
        assertThat(saved.getLastPlayed()).isNull();
        // Re-read: the add() result carries the in-memory nanosecond stamp, the column holds microseconds
        LocalDateTime stamped = libraryService.getGame(ALICE, saved.getId()).getStatusChangedAt();
        assertThat(stamped).isNotNull();

        UpdateGameRequest notesOnly = new UpdateGameRequest();
        notesOnly.setNotes("still on the shelf");
        assertThat(libraryService.updateGame(ALICE, saved.getId(), notesOnly).getStatusChangedAt())
                .isEqualTo(stamped);

        UpdateGameRequest toPlaying = new UpdateGameRequest();
        toPlaying.setStatus(GameStatus.PLAYING);
        toPlaying.setRating(9);
        UserGameDTO updated = libraryService.updateGame(ALICE, saved.getId(), toPlaying);

        assertThat(updated.getStatus()).isEqualTo(GameStatus.PLAYING);
        assertThat(updated.getRating()).isEqualTo(9);
        assertThat(updated.getLastPlayed()).isNotNull();
        assertThat(updated.getStatusChangedAt()).isAfter(stamped);
        assertThat(updated.getPreviousStatus()).isEqualTo(GameStatus.BACKLOG);

        UpdateGameRequest toDusty = new UpdateGameRequest();
        toDusty.setStatus(GameStatus.DUSTY);
        assertThatThrownBy(() -> libraryService.updateGame(ALICE, saved.getId(), toDusty))
                .isInstanceOf(IllegalArgumentException.class);

        libraryService.removeGame(ALICE, saved.getId());
        assertThatThrownBy(() -> libraryService.getGame(ALICE, saved.getId()))
                .isInstanceOf(GameNotFoundException.class);
    }

    // The nightly job moves BACKLOG and PLAYING rows whose status has stood for 90 days to DUSTY.
    // Time is faked by backdating status_changed_at in SQL. A rating edit in between must not
    // rescue the row: only a status change moves the clock.
    @Test
    void theDustyJobMovesOnlyUntouchedBacklogAndPlayingRows() {
        UserGameDTO forgottenBacklog = add(ALICE, 1001, GameStatus.BACKLOG);
        UserGameDTO forgottenPlaying = add(ALICE, 1002, GameStatus.PLAYING);
        UserGameDTO oldButCompleted = add(ALICE, 1003, GameStatus.COMPLETED);
        UserGameDTO recentBacklog = add(BOB, 1001, GameStatus.BACKLOG);

        Timestamp hundredDaysAgo = Timestamp.valueOf(LocalDateTime.now().minusDays(100));
        for (UserGameDTO stale : List.of(forgottenBacklog, forgottenPlaying, oldButCompleted)) {
            jdbc.update("UPDATE user_games SET status_changed_at = ? WHERE id = ?", hundredDaysAgo, stale.getId());
        }
        UpdateGameRequest ratingOnly = new UpdateGameRequest();
        ratingOnly.setRating(6);
        libraryService.updateGame(ALICE, forgottenBacklog.getId(), ratingOnly);

        dustyScheduler.transitionDustyGames();

        assertThat(libraryService.getDustyGames(ALICE))
                .extracting(UserGameDTO::getIgdbGameId).containsExactlyInAnyOrder(1001, 1002);
        assertThat(libraryService.getGame(ALICE, forgottenBacklog.getId()).getPreviousStatus()).isEqualTo(GameStatus.BACKLOG);
        assertThat(libraryService.getGame(ALICE, forgottenPlaying.getId()).getPreviousStatus()).isEqualTo(GameStatus.PLAYING);
        assertThat(libraryService.getGame(ALICE, oldButCompleted.getId()).getStatus()).isEqualTo(GameStatus.COMPLETED);
        assertThat(libraryService.getGame(BOB, recentBacklog.getId()).getStatus()).isEqualTo(GameStatus.BACKLOG);
        assertThat(libraryService.getDustyGames(BOB)).isEmpty();
    }

    // Replace-all deletes then inserts inside one transaction. Hibernate would order the
    // inserts first and trip the (user_id, name) unique constraint on every overlapping value
    // unless the repositories flush the delete explicitly; this is the case that proves they do.
    @Test
    void preferencesCanBeReplacedWithOverlappingValuesInOneTransaction() {
        genrePreferenceService.replacePreferences(ALICE, List.of("Action", "Role-playing (RPG)"));
        tagPreferenceService.replacePreferences(ALICE, List.of("cozy", "roguelike"));
        releaseYearPreferenceService.replacePreferences(ALICE, List.of("2010s", "2020s"));

        List<UserGenrePreferenceDTO> genres = genrePreferenceService.replacePreferences(ALICE,
                List.of("Role-playing (RPG)", "Strategy", " Strategy ", ""));
        List<UserTagPreferenceDTO> tags = tagPreferenceService.replacePreferences(ALICE,
                List.of("roguelike", "metroidvania"));
        List<UserReleaseYearPreferenceDTO> years = releaseYearPreferenceService.replacePreferences(ALICE,
                List.of("2020s", "1990s"));

        assertThat(genres).extracting(UserGenrePreferenceDTO::genreName)
                .containsExactlyInAnyOrder("Role-playing (RPG)", "Strategy");
        assertThat(genrePreferenceService.getPreferences(ALICE)).hasSize(2);
        assertThat(tags).extracting(UserTagPreferenceDTO::tagName)
                .containsExactlyInAnyOrder("roguelike", "metroidvania");
        assertThat(years).extracting(UserReleaseYearPreferenceDTO::bucketLabel)
                .containsExactlyInAnyOrder("2020s", "1990s");
        assertThat(genrePreferenceService.getPreferences(BOB)).isEmpty();
    }

    @Test
    void purgingAnAccountEmptiesEveryTableAndTheExportAgrees() {
        add(ALICE, 1001, GameStatus.BACKLOG);
        add(ALICE, 1002, GameStatus.COMPLETED);
        genrePreferenceService.replacePreferences(ALICE, List.of("Action"));
        tagPreferenceService.replacePreferences(ALICE, List.of("cozy"));
        releaseYearPreferenceService.replacePreferences(ALICE, List.of("2010s"));
        add(BOB, 1003, GameStatus.PLAYING);

        AccountExportDTO before = accountService.exportUser(ALICE);
        assertThat(before.gameCount()).isEqualTo(2);
        assertThat(before.genrePreferenceCount()).isEqualTo(1);
        assertThat(before.releaseYearPreferenceCount()).isEqualTo(1);

        AccountService.PurgeResult purged = accountService.purgeUser(ALICE);

        assertThat(purged.gamesRemoved()).isEqualTo(2);
        assertThat(purged.genrePreferencesRemoved()).isEqualTo(1);
        assertThat(purged.tagPreferencesRemoved()).isEqualTo(1);
        assertThat(purged.releaseYearPreferencesRemoved()).isEqualTo(1);
        AccountExportDTO after = accountService.exportUser(ALICE);
        assertThat(after.gameCount()).isZero();
        assertThat(after.genrePreferenceCount()).isZero();
        // The join tables hang off user_games and must go with it.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_game_genres", Integer.class)).isEqualTo(1);
        assertThat(libraryService.getGames(BOB, null, null, null, null, TOKEN)).hasSize(1);
    }

    // The ledger row is the only thing that survives a deletion request, and it must not
    // be visible to the retry job until the grace window has passed.
    @Test
    void aDeletionRequestLeavesALedgerRowThatOutlivesThePurgeAndSurfacesAfterTheGrace() {
        add(ALICE, 1001, GameStatus.BACKLOG);
        try {
            accountService.requestDeletion(ALICE);

            assertThat(libraryService.getGames(ALICE, null, null, null, null, TOKEN)).isEmpty();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM account_deletions WHERE user_id = ? AND identity_deleted_at IS NULL",
                    Integer.class, ALICE)).isEqualTo(1);
            assertThat(accountService.pendingDeletions()).isEmpty();

            jdbc.update("UPDATE account_deletions SET requested_at = ? WHERE user_id = ?",
                    Timestamp.valueOf(LocalDateTime.now().minusMinutes(5)), ALICE);
            assertThat(accountService.pendingDeletions())
                    .extracting(AccountDeletionDTO::userId).containsExactly(ALICE);

            accountService.completeDeletion(ALICE);
            assertThat(accountService.pendingDeletions()).isEmpty();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM account_deletions WHERE user_id = ? AND identity_deleted_at IS NOT NULL",
                    Integer.class, ALICE)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM account_deletions WHERE user_id = ?", ALICE);
        }
    }
}
