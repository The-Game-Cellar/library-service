package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.support.LibraryServiceTestBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// With no shared token configured the filter fails closed: no header value, empty or not, gets through.
@TestPropertySource(properties = "security.internal.token=")
class InternalTokenNotConfiguredTest extends LibraryServiceTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", INTERNAL_TOKEN})
    void everyInternalRequestIsRefused(String supplied) throws Exception {
        mvc.perform(get("/internal/library/users/" + ALICE + "/games").header(INTERNAL_HEADER, supplied))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/library/account-deletions/pending")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/library/account-deletions/" + ALICE + "/complete").header(INTERNAL_HEADER, supplied))
                .andExpect(status().isUnauthorized());
    }
}
