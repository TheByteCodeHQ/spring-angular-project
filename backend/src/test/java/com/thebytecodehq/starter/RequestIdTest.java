package com.thebytecodehq.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class RequestIdTest extends ApiTestSupport {

    @Test
    void aSafeIdSuppliedByTheCallerIsKeptAndEchoed() throws Exception {
        mvc.perform(getAs("/api/tasks", user()).header("X-Request-Id", "trace-12345678"))
                .andExpect(header().string("X-Request-Id", "trace-12345678"));
    }

    @Test
    void anUnsafeOrMissingIdIsReplacedByAGeneratedOne() throws Exception {
        for (String unsafe : new String[] {"short", "has space in it!!", "x".repeat(200), "line\\nbreak-injection"}) {
            MvcResult result = mvc.perform(getAs("/api/tasks", user()).header("X-Request-Id", unsafe)).andReturn();
            String id = result.getResponse().getHeader("X-Request-Id");
            assertThat(id).isNotEqualTo(unsafe).matches("^[0-9a-f-]{36}$");
        }
        assertThat(mvc.perform(getAs("/api/tasks", user())).andReturn().getResponse().getHeader("X-Request-Id"))
                .isNotBlank();
    }

    @Test
    void theSameIdAppearsInErrorBodies() throws Exception {
        mvc.perform(getAs("/api/tasks/not-a-uuid", user()).header("X-Request-Id", "trace-87654321"))
                .andExpect(header().string("X-Request-Id", "trace-87654321"))
                .andExpect(jsonPath("$.requestId").value("trace-87654321"));
    }
}
