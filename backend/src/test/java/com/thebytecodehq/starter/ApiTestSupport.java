package com.thebytecodehq.starter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thebytecodehq.starter.security.JwtService;
import com.thebytecodehq.starter.task.TaskRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability   // Spring Boot switches metrics export off in tests; the Prometheus endpoint needs it on
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JwtService jwt;
    @Autowired protected TaskRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    protected String bearer(String user, String... roles) {
        return "Bearer " + jwt.mint(user, List.of(roles));
    }

    protected String user() {
        return bearer("alice", "USER");
    }

    protected String admin() {
        return bearer("root", "ADMIN");
    }

    protected MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, String auth, Object body)
            throws Exception {
        return request.header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : json.writeValueAsString(body));
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** Creates a task through the API and returns the response body. */
    protected JsonNode createTask(String auth, String title, String status) throws Exception {
        Map<String, Object> request = status == null ? Map.of("title", title) : Map.of("title", title, "status", status);
        return body(mvc.perform(withJson(post("/api/tasks"), auth, request)).andReturn());
    }

    protected MockHttpServletRequestBuilder getAs(String url, String auth) {
        return get(url).header(HttpHeaders.AUTHORIZATION, auth);
    }

    protected MockHttpServletRequestBuilder deleteAs(String url, String auth) {
        return delete(url).header(HttpHeaders.AUTHORIZATION, auth);
    }

    protected MockHttpServletRequestBuilder putAs(String url, String auth, Object body) throws Exception {
        return withJson(put(url), auth, body);
    }
}
