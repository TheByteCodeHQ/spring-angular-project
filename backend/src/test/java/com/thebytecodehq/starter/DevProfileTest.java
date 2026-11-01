package com.thebytecodehq.starter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/** The dev profile: a token endpoint for local use, and CORS for the Angular dev server. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevProfileTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode token(String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/dev/token").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void mintsATokenThatWorksAgainstTheApi() throws Exception {
        JsonNode token = token("{\"username\":\"dev-user\",\"roles\":[\"USER\"]}");
        org.junit.jupiter.api.Assertions.assertEquals(3600, token.get("expiresIn").asLong());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/tasks")
                        .header("Authorization", "Bearer " + token.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void defaultsToTheUserRole() throws Exception {
        String bearer = "Bearer " + token("{\"username\":\"someone\"}").get("accessToken").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/tasks/00000000-0000-0000-0000-000000000000").header("Authorization", bearer))
                .andExpect(status().isForbidden());   // USER, not ADMIN
    }

    @Test
    void refusesUnknownRolesAndBlankNames() throws Exception {
        for (Map<String, Object> bad : new Map[] {Map.of("username", "x", "roles", new String[] {"SUPERUSER"}),
                Map.of("username", " ")}) {
            mvc.perform(post("/api/dev/token").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Validation failed"));
        }
    }

    @Test
    void allowsTheConfiguredFrontendOriginAndNoOther() throws Exception {
        mvc.perform(options("/api/tasks").header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(options("/api/tasks").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
