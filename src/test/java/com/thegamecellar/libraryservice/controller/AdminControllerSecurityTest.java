package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.model.enums.GameStatus;
import com.thegamecellar.libraryservice.support.LibraryServiceTestBase;
import com.thegamecellar.libraryservice.support.StubHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The admin prefix is guarded by ROLE_ADMIN, which the app's converter reads from the token's realm roles.
class AdminControllerSecurityTest extends LibraryServiceTestBase {

    private static final String REFRESH = "/api/v1/library/admin/refresh-game-info";

    @Test
    void withoutATokenOrWithARejectedOneTheRefreshIs401() throws Exception {
        mvc.perform(post(REFRESH)).andExpect(status().isUnauthorized());
        mvc.perform(post(REFRESH).header(HttpHeaders.AUTHORIZATION, bearer("forged"))).andExpect(status().isUnauthorized());
        assertThat(GAME_SERVICE.recorded()).isEmpty();
    }

    @Test
    void aUserWithoutTheAdminRealmRoleGets403AndNothingIsAsked() throws Exception {
        entry(ALICE, 1001, "Hollow Knight", GameStatus.BACKLOG, "PC");

        mvc.perform(post(REFRESH).header(HttpHeaders.AUTHORIZATION, bearer(ALICE_TOKEN)))
                .andExpect(status().isForbidden());
        assertThat(GAME_SERVICE.recorded()).isEmpty();
    }

    // Every user's rows are refreshed with the admin's own token forwarded to game-service. A row
    // game-service cannot answer for is left as it is and counted as a miss.
    @Test
    void theAdminRefreshRewritesChangedCatalogFieldsAcrossEveryLibrary() throws Exception {
        entry(ALICE, 1001, "Hollow Knight", GameStatus.BACKLOG, "PC");
        entry(BOB, 1001, "Hollow Knight", GameStatus.PLAYING, "Nintendo Switch");
        entry(BOB, 1002, "Not Cached Upstream", GameStatus.WISHLIST, "PC");
        catalogGame(1001, "Hollow Knight", List.of("Platform", "Adventure"), List.of("Action"), List.of("metroidvania"), "2017-02-24");
        GAME_SERVICE.on("GET", "/api/v1/games/1002", StubHttpServer.StubResponse.json(404, "{}"));

        mvc.perform(post(REFRESH).header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examined").value(3))
                .andExpect(jsonPath("$.updated").value(2))
                .andExpect(jsonPath("$.genresChanged").value(2))
                .andExpect(jsonPath("$.gameServiceMisses").value(1));

        assertThat(jdbc.queryForList("""
                SELECT g.user_id || ':' || ge.genres FROM user_game_genres ge JOIN user_games g ON g.id = ge.user_game_id
                ORDER BY g.user_id, ge.genres
                """, String.class)).containsExactly(
                ALICE + ":Adventure", ALICE + ":Platform", BOB + ":Adventure", BOB + ":Platform");
        assertThat(GAME_SERVICE.recorded("GET", "/api/v1/games/"))
                .allMatch(r -> bearer(ADMIN_TOKEN).equals(r.header("Authorization")));
    }
}
