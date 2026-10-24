package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.entity.UserGame;
import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.support.LibraryServiceTestBase;
import com.thegamecellar.libraryservice.support.StubHttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The library endpoints driven as the gateway calls them, Alice acting with Bob's library beside hers.
class LibraryEndpointsTest extends LibraryServiceTestBase {

    private static final String LIBRARY = "/api/v1/library";

    private long hollowKnight;
    private long bobsStardew;

    @BeforeEach
    void twoLibraries() {
        hollowKnight = synced(ALICE, 1001, "Hollow Knight", GameStatus.BACKLOG, new BigDecimal("9.0"),
                List.of("Platform", "Adventure"), "PC", "PlayStation 5");
        synced(ALICE, 1003, "Celeste", GameStatus.COMPLETED, new BigDecimal("8.5"), List.of("Platform"), "PC");
        synced(ALICE, 1004, "Elden Ring", GameStatus.PLAYING, null, List.of("Role-playing (RPG)"), "PlayStation 5");
        synced(ALICE, 1005, "Forgotten", GameStatus.DUSTY, null, List.of(), "PC");
        synced(ALICE, 1006, "Someday", GameStatus.WISHLIST, null, List.of(), "PC");
        bobsStardew = synced(BOB, 2002, "Stardew Valley", GameStatus.BACKLOG, new BigDecimal("10"),
                List.of("Simulator"), "Nintendo Switch");
    }

    private long synced(String userId, int igdbId, String name, GameStatus status, BigDecimal rating,
                        List<String> genres, String... platformNames) {
        return games.save(UserGame.builder().userId(userId).igdbGameId(igdbId).gameName(name).status(status)
                .rating(rating).genres(new ArrayList<>(genres)).platforms(new ArrayList<>(List.of(platformNames)))
                .metadataSyncedAt(LocalDateTime.now()).build()).getId();
    }

    private ResultActions asAlice(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN)));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ── reads ─────────────────────────────────────────────────────────────────

    @Test
    void theLibraryListsTheCallersEntriesOnly() throws Exception {
        asAlice(get(LIBRARY + "/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].igdbGameId", containsInAnyOrder(1001, 1003, 1004, 1005, 1006)));
    }

    @Test
    void theLibraryFiltersByStatusPlatformSearchAndGenre() throws Exception {
        asAlice(get(LIBRARY + "/games").param("status", "PLAYING"))
                .andExpect(jsonPath("$[*].igdbGameId", contains(1004)));
        asAlice(get(LIBRARY + "/games").param("platform", "PlayStation 5"))
                .andExpect(jsonPath("$[*].igdbGameId", containsInAnyOrder(1001, 1004)));
        asAlice(get(LIBRARY + "/games").param("platform", "Nintendo Switch"))
                .andExpect(jsonPath("$[*].igdbGameId").isEmpty());
        asAlice(get(LIBRARY + "/games").param("search", "CELES"))
                .andExpect(jsonPath("$[*].igdbGameId", contains(1003)));
        asAlice(get(LIBRARY + "/games").param("genre", "platform"))
                .andExpect(jsonPath("$[*].igdbGameId", containsInAnyOrder(1001, 1003)));
    }

    // A row added while game-service was down is filled in on the next read, with the caller's
    // token and the request id forwarded.
    @Test
    void aRowMissingItsCatalogMetadataIsHealedOnReadThroughGameService() throws Exception {
        long half = entry(ALICE, 1007, "Half Added", GameStatus.BACKLOG, "PC").getId();
        catalogGame(1007, "Half Added", List.of("Shooter"), List.of("Sci-fi"), List.of("fast"), "2020-01-01");

        mvc.perform(get(LIBRARY + "/games").param("search", "half")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN)).header("X-Request-ID", "req-lib-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].genres", contains("Shooter")))
                .andExpect(jsonPath("$[0].released").value("2020-01-01"));

        assertThat(games.findById(half).orElseThrow().getMetadataSyncedAt()).isNotNull();
        StubHttpServer.RecordedRequest asked = GAME_SERVICE.recorded("GET", "/api/v1/games/1007").get(0);
        assertThat(asked.header("Authorization")).isEqualTo(bearer(ALICE_TOKEN));
        assertThat(asked.header("X-Request-ID")).isEqualTo("req-lib-1");
    }

    @Test
    void genresPlatformsAndIgdbIdsAreDrawnFromTheCallersEntries() throws Exception {
        asAlice(get(LIBRARY + "/genres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("Adventure", "Platform", "Role-playing (RPG)")));
        asAlice(get(LIBRARY + "/games/platforms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("PC", "PlayStation 5")));
        asAlice(get(LIBRARY + "/igdb-ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", containsInAnyOrder(1001, 1003, 1004, 1005, 1006)));
    }

    @Test
    void eachStatusListHoldsOnlyTheCallersEntriesInThatStatus() throws Exception {
        asAlice(get(LIBRARY + "/backlog")).andExpect(status().isOk()).andExpect(jsonPath("$[*].igdbGameId", contains(1001)));
        asAlice(get(LIBRARY + "/wishlist")).andExpect(status().isOk()).andExpect(jsonPath("$[*].igdbGameId", contains(1006)));
        asAlice(get(LIBRARY + "/playing")).andExpect(status().isOk()).andExpect(jsonPath("$[*].igdbGameId", contains(1004)));
        asAlice(get(LIBRARY + "/completed")).andExpect(status().isOk()).andExpect(jsonPath("$[*].igdbGameId", contains(1003)));
        asAlice(get(LIBRARY + "/dusty")).andExpect(status().isOk()).andExpect(jsonPath("$[*].igdbGameId", contains(1005)));
    }

    // A game on two platforms counts under both, so the platform numbers sum past the total.
    @Test
    void statsCountTheCallersLibrary() throws Exception {
        asAlice(get(LIBRARY + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGames").value(5))
                .andExpect(jsonPath("$.byStatus.BACKLOG").value(1))
                .andExpect(jsonPath("$.byStatus.DUSTY").value(1))
                .andExpect(jsonPath("$.averageRating").value(8.75))
                .andExpect(jsonPath("$.totalRated").value(2))
                .andExpect(jsonPath("$.byGenre.Platform").value(2))
                .andExpect(jsonPath("$.byPlatform.PC").value(4))
                .andExpect(jsonPath("$.byPlatform['PlayStation 5']").value(2));
    }

    @Test
    void anEntryIsReadByIdOrByIgdbIdOnlyByItsOwner() throws Exception {
        asAlice(get(LIBRARY + "/games/" + hollowKnight))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameName").value("Hollow Knight"))
                .andExpect(jsonPath("$.platforms", contains("PC", "PlayStation 5")))
                .andExpect(jsonPath("$.platform").value("PC"));
        asAlice(get(LIBRARY + "/games/by-igdb/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hollowKnight));

        // Bob's row is answered exactly as a row that does not exist.
        asAlice(get(LIBRARY + "/games/" + bobsStardew))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Game not found in collection: " + bobsStardew));
        asAlice(get(LIBRARY + "/games/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Game not found in collection: 999999"));
        asAlice(get(LIBRARY + "/games/by-igdb/2002")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/games/0", "/games/-1", "/games/abc", "/games/by-igdb/0", "/games/by-igdb/x"})
    void anIdThatIsNotAPositiveNumberIs400(String path) throws Exception {
        asAlice(get(LIBRARY + path)).andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownStatusFilterIs400() throws Exception {
        asAlice(get(LIBRARY + "/games").param("status", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ── add ───────────────────────────────────────────────────────────────────

    @Test
    void addingAGameCachesTheCatalogDataPublishesTheCallerAndAnswers201() throws Exception {
        catalogGame(1010, "Real Name", List.of("Puzzle"), List.of(), List.of("cozy"), "2021-05-05");

        asAlice(json(post(LIBRARY + "/games"), """
                {"igdbGameId": 1010, "gameName": "Typed Name", "status": "BACKLOG",
                 "platforms": ["Xbox 360"], "rating": 7.5, "notes": "gift"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.gameName").value("Real Name"))
                .andExpect(jsonPath("$.genres", contains("Puzzle")))
                .andExpect(jsonPath("$.rating").value(7.5))
                .andExpect(jsonPath("$.notes").value("gift"));

        assertThat(libraryWrites()).containsExactly(ALICE);
        assertThat(GAME_SERVICE.recorded("GET", "/api/v1/games/1010").get(0).header("Authorization"))
                .isEqualTo(bearer(ALICE_TOKEN));
    }

    // game-service down is not the caller's problem: the entry is kept under the typed name and
    // marked for healing on a later read.
    @Test
    void addingWhileGameServiceFailsKeepsTheTypedNameAndLeavesTheRowToHeal() throws Exception {
        GAME_SERVICE.on("GET", "/api/v1/games/1011", StubHttpServer.StubResponse.json(500, "{}"));

        asAlice(json(post(LIBRARY + "/games"), """
                {"igdbGameId": 1011, "gameName": "Typed Name", "status": "WISHLIST", "platform": "PC"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameName").value("Typed Name"));

        UserGame saved = games.findByUserIdAndIgdbGameId(ALICE, 1011).orElseThrow();
        assertThat(saved.getMetadataSyncedAt()).isNull();
    }

    @Test
    void addingAGameAlreadyInTheLibraryIs409AndAsksNobody() throws Exception {
        asAlice(json(post(LIBRARY + "/games"), """
                {"igdbGameId": 1001, "gameName": "Hollow Knight", "status": "PLAYING", "platform": "PC"}
                """))
                .andExpect(status().isConflict());

        assertThat(GAME_SERVICE.recorded()).isEmpty();
        assertThat(libraryWrites()).isEmpty();
        assertThat(games.findById(hollowKnight).orElseThrow().getStatus()).isEqualTo(GameStatus.BACKLOG);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"gameName\": \"X\", \"status\": \"BACKLOG\", \"platform\": \"PC\"}",
            "{\"igdbGameId\": 1020, \"gameName\": \" \", \"status\": \"BACKLOG\", \"platform\": \"PC\"}",
            "{\"igdbGameId\": 1020, \"gameName\": \"X\", \"platform\": \"PC\"}",
            "{\"igdbGameId\": 1020, \"gameName\": \"X\", \"status\": \"BACKLOG\"}",
            "{\"igdbGameId\": 1020, \"gameName\": \"X\", \"status\": \"BACKLOG\", \"platforms\": [\" \"]}",
            "{\"igdbGameId\": 1020, \"gameName\": \"X\", \"status\": \"FOO\", \"platform\": \"PC\"}",
            "{\"igdbGameId\": \"many\", \"gameName\": \"X\", \"status\": \"BACKLOG\", \"platform\": \"PC\"}",
            "{\"igdbGameId\": 1020,",
            ""})
    void anAddWithAMissingOrMalformedFieldIs400AndStoresNothing(String body) throws Exception {
        asAlice(json(post(LIBRARY + "/games"), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertThat(games.findByUserIdAndIgdbGameId(ALICE, 1020)).isEmpty();
        assertThat(libraryWrites()).isEmpty();
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void movingAnEntryToPlayingStampsItAndPublishesTheCaller() throws Exception {
        asAlice(json(put(LIBRARY + "/games/" + hollowKnight), "{\"status\": \"PLAYING\", \"playtime\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAYING"))
                .andExpect(jsonPath("$.previousStatus").value("BACKLOG"))
                .andExpect(jsonPath("$.lastPlayed").value(notNullValue()))
                .andExpect(jsonPath("$.playtime").value(3));

        assertThat(libraryWrites()).containsExactly(ALICE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\": \"DUSTY\"}", "{\"playtime\": -1}", "{\"platforms\": []}",
            "{\"status\": \"FOO\"}", "{\"rating\": 11}", "{\"status\":"})
    void anUpdateTheRulesRefuseIs400AndChangesNothing(String body) throws Exception {
        asAlice(json(put(LIBRARY + "/games/" + hollowKnight), body))
                .andExpect(status().isBadRequest());

        UserGame unchanged = games.findById(hollowKnight).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(GameStatus.BACKLOG);
        assertThat(unchanged.getPlatforms()).containsExactly("PC", "PlayStation 5");
        assertThat(libraryWrites()).isEmpty();
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void removingAnEntryAnswers204AndPublishesTheCaller() throws Exception {
        asAlice(delete(LIBRARY + "/games/" + hollowKnight)).andExpect(status().isNoContent());

        assertThat(games.findById(hollowKnight)).isEmpty();
        assertThat(libraryWrites()).containsExactly(ALICE);
        asAlice(delete(LIBRARY + "/games/0")).andExpect(status().isBadRequest());
    }

    // ── malformed requests ────────────────────────────────────────────────────

    // A malformed request is the caller's error: 405 or 415, never a 500 that pages as a server fault.
    @Test
    void aWrongMethodOrAWrongMediaTypeIsRefusedAsTheCallersError() throws Exception {
        asAlice(delete(LIBRARY + "/stats"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET"));
        asAlice(post(LIBRARY + "/games").contentType(MediaType.TEXT_PLAIN).content("igdbGameId=1"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ── lengths ───────────────────────────────────────────────────────────────

    // The app's notes field takes 10 000 characters, and the API stores every one of them.
    @Test
    void aNoteAsLongAsTheAppAllowsIsStoredWholeOnAddAndOnUpdate() throws Exception {
        String longest = "n".repeat(10_000);

        asAlice(json(post(LIBRARY + "/games"), """
                {"igdbGameId": 1030, "gameName": "Noted", "status": "BACKLOG", "platform": "PC", "notes": "%s"}
                """.formatted(longest)))
                .andExpect(status().isCreated());
        asAlice(json(put(LIBRARY + "/games/" + hollowKnight), "{\"notes\": \"%s\"}".formatted(longest)))
                .andExpect(status().isOk());

        assertThat(games.findByUserIdAndIgdbGameId(ALICE, 1030).orElseThrow().getNotes()).isEqualTo(longest);
        assertThat(games.findById(hollowKnight).orElseThrow().getNotes()).isEqualTo(longest);
    }

    @Test
    void aNoteOverTheLimitIs400AndStoresNothing() throws Exception {
        String tooLong = "n".repeat(10_001);

        asAlice(json(post(LIBRARY + "/games"), """
                {"igdbGameId": 1031, "gameName": "Noted", "status": "BACKLOG", "platform": "PC", "notes": "%s"}
                """.formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].path").value("notes"));
        asAlice(json(put(LIBRARY + "/games/" + hollowKnight), "{\"notes\": \"%s\"}".formatted(tooLong)))
                .andExpect(status().isBadRequest());

        assertThat(games.findByUserIdAndIgdbGameId(ALICE, 1031)).isEmpty();
        assertThat(games.findById(hollowKnight).orElseThrow().getNotes()).isNull();
    }

    // Longer than the column would be a database error; it is the caller's error and answers 400.
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"igdbGameId\": 1032, \"gameName\": \"%s\", \"status\": \"BACKLOG\", \"platform\": \"PC\"}",
            "{\"igdbGameId\": 1032, \"gameName\": \"X\", \"status\": \"BACKLOG\", \"platform\": \"%s\"}",
            "{\"igdbGameId\": 1032, \"gameName\": \"X\", \"status\": \"BACKLOG\", \"platforms\": [\"PC\", \"%s\"]}"})
    void aNameLongerThanItsColumnIs400OnAdd(String template) throws Exception {
        asAlice(json(post(LIBRARY + "/games"), template.formatted("P".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
        assertThat(games.findByUserIdAndIgdbGameId(ALICE, 1032)).isEmpty();
    }

    @Test
    void aPlatformNameLongerThanItsColumnIs400OnUpdate() throws Exception {
        asAlice(json(put(LIBRARY + "/games/" + hollowKnight), "{\"platforms\": [\"%s\"]}".formatted("P".repeat(256))))
                .andExpect(status().isBadRequest());
        asAlice(json(put(LIBRARY + "/games/" + hollowKnight), "{\"platform\": \"%s\"}".formatted("P".repeat(256))))
                .andExpect(status().isBadRequest());
        assertThat(games.findById(hollowKnight).orElseThrow().getPlatforms()).containsExactly("PC", "PlayStation 5");
    }
}
