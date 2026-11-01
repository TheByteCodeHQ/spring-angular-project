package com.thebytecodehq.starter;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.thebytecodehq.starter.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ErrorHandlingTest {

    @RestController
    static class Thrower {
        @GetMapping("/lost-update")
        String lostUpdate() {
            throw new OptimisticLockingFailureException("row was updated or deleted by another transaction (table tasks)");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("jdbc:postgresql://db.internal:5432/prod password=hunter2");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Thrower())
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void aLostUpdateAtTheDatabaseLevelBecomesA409WithoutInternals() throws Exception {
        mvc.perform(get("/lost-update"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Edit conflict"))
                .andExpect(content().string(not(containsString("table tasks"))));
    }

    @Test
    void anUnexpectedFailureIsA500ThatLeaksNothing() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Unexpected error"))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("db.internal"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
