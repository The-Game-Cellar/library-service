package com.thegamecellar.libraryservice.controller;

import com.thegamecellar.libraryservice.service.LibraryAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.datasource.password=",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://unused"
})
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LibraryAdminService libraryAdminService;

    @Test
    void adminEndpoint_withoutAuthentication_returns401() throws Exception {
        mvc.perform(post("/api/v1/library/admin/refresh-game-info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_withoutAdminRole_returns403() throws Exception {
        mvc.perform(post("/api/v1/library/admin/refresh-game-info")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_withAdminRole_returns200() throws Exception {
        when(libraryAdminService.refreshGameInfo(anyString())).thenReturn(Map.of("processed", 0));

        mvc.perform(post("/api/v1/library/admin/refresh-game-info")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
