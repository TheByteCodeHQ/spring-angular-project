package com.thebytecodehq.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TaskApiTest extends ApiTestSupport {

    @Test
    void createsATaskAndRecordsWhoCreatedItFromTheToken() throws Exception {
        mvc.perform(withJson(post("/api/tasks"), user(),
                        Map.of("title", "  Write the docs  ", "description", "  for v1 ", "dueDate", "2030-01-31")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/tasks/")))
                .andExpect(jsonPath("$.title").value("Write the docs"))      // trimmed
                .andExpect(jsonPath("$.description").value("for v1"))
                .andExpect(jsonPath("$.status").value("TODO"))               // default
                .andExpect(jsonPath("$.createdBy").value("alice"))           // from the token, not the body
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createdByCannotBeSetByTheClient() throws Exception {
        mvc.perform(withJson(post("/api/tasks"), user(), Map.of("title", "x1", "createdBy", "mallory")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value("alice"));
    }

    @Test
    void rejectsInvalidInputWithAProblemDocumentListingEachField() throws Exception {
        mvc.perform(withJson(post("/api/tasks"), user(), Map.of("title", " ", "description", "d".repeat(2001))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void rejectsMalformedJsonAndUnknownEnumValuesWithoutLeakingInternals() throws Exception {
        mvc.perform(withJson(post("/api/tasks"), user(), "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The request body could not be read"));
        mvc.perform(withJson(post("/api/tasks"), user(), Map.of("title", "x", "status", "ARCHIVED")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Exception"))));
    }

    @Test
    void getsATaskAndReportsMissingOnesAsProblems() throws Exception {
        String id = createTask(user(), "Find me", null).get("id").asText();
        mvc.perform(getAs("/api/tasks/" + id, user())).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Find me"));
        mvc.perform(getAs("/api/tasks/00000000-0000-0000-0000-000000000000", user()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not found"));
        mvc.perform(getAs("/api/tasks/not-a-uuid", user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    void updatesATaskAndBumpsItsVersion() throws Exception {
        JsonNode task = createTask(user(), "Draft", null);
        Map<String, Object> change = Map.of("title", "Final", "status", "DONE", "version", task.get("version").asLong());
        mvc.perform(putAs("/api/tasks/" + task.get("id").asText(), user(), change))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Final"))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void staleUpdatesAreRefusedInsteadOfOverwritingSomeoneElsesWork() throws Exception {
        JsonNode task = createTask(user(), "Shared", null);
        String url = "/api/tasks/" + task.get("id").asText();
        mvc.perform(putAs(url, user(), Map.of("title", "First edit", "status", "TODO", "version", 0))).andExpect(status().isOk());
        mvc.perform(putAs(url, user(), Map.of("title", "Second edit", "status", "TODO", "version", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Edit conflict"));
        mvc.perform(getAs(url, user())).andExpect(jsonPath("$.title").value("First edit"));
    }

    @Test
    void updateRequiresTheVersion() throws Exception {
        String id = createTask(user(), "Needs version", null).get("id").asText();
        mvc.perform(putAs("/api/tasks/" + id, user(), Map.of("title", "x1", "status", "TODO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
    }

    @Test
    void changesStatusWithAPatch() throws Exception {
        String id = createTask(user(), "Move me", null).get("id").asText();
        mvc.perform(withJson(patch("/api/tasks/" + id + "/status"), user(), Map.of("status", "IN_PROGRESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(withJson(patch("/api/tasks/" + id + "/status"), user(), Map.of()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdministratorsCanDelete() throws Exception {
        String id = createTask(user(), "Delete me", null).get("id").asText();
        mvc.perform(deleteAs("/api/tasks/" + id, user())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        assertThat(repository.count()).isEqualTo(1);
        mvc.perform(deleteAs("/api/tasks/" + id, admin())).andExpect(status().isNoContent());
        assertThat(repository.count()).isZero();
        mvc.perform(deleteAs("/api/tasks/" + id, admin())).andExpect(status().isNotFound());
    }

    @Test
    void filtersByStatusAndByTitleTextCaseInsensitively() throws Exception {
        createTask(user(), "Fix login bug", "TODO");
        createTask(user(), "Write LOGIN tests", "IN_PROGRESS");
        createTask(user(), "Deploy service", "DONE");
        mvc.perform(getAs("/api/tasks?q=login", user())).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(getAs("/api/tasks?status=DONE", user())).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Deploy service"));
        mvc.perform(getAs("/api/tasks?q=login&status=TODO", user())).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(getAs("/api/tasks?status=NOPE", user())).andExpect(status().isBadRequest());
    }

    @Test
    void searchTextIsDataNotAWildcard() throws Exception {
        createTask(user(), "100% done", null);
        createTask(user(), "plain title", null);
        mvc.perform(getAs("/api/tasks", user()).param("q", "%")).andExpect(jsonPath("$.totalElements").value(1));   // literal '%'
        mvc.perform(getAs("/api/tasks", user()).param("q", "_")).andExpect(jsonPath("$.totalElements").value(0));   // literal '_'
        mvc.perform(getAs("/api/tasks", user()).param("q", "plain")).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pagesAndSortsWithABoundedAllowList() throws Exception {
        for (String title : new String[] {"banana", "apple", "cherry"}) {
            createTask(user(), title, null);
        }
        mvc.perform(getAs("/api/tasks?sort=title,asc&size=2", user()))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("apple"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(getAs("/api/tasks?sort=title,desc&size=2&page=1", user()))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("apple"));
        mvc.perform(getAs("/api/tasks?sort=createdBy,asc", user())).andExpect(status().isBadRequest());   // not allow-listed
        mvc.perform(getAs("/api/tasks?sort=title;drop,asc", user())).andExpect(status().isBadRequest());
        mvc.perform(getAs("/api/tasks?size=101", user())).andExpect(status().isBadRequest());
        mvc.perform(getAs("/api/tasks?size=0", user())).andExpect(status().isBadRequest());
        mvc.perform(getAs("/api/tasks?page=-1", user())).andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedMethodsAndMediaTypesGetProblemDocumentsToo() throws Exception {
        mvc.perform(patch("/api/tasks").header("Authorization", user()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/api/tasks").header("Authorization", user()).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
