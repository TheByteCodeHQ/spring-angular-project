package com.thebytecodehq.starter;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SecurityRulesTest extends ApiTestSupport {

    private static final String OTHER_SECRET = "a-completely-different-secret-0123456789abcdef";

    private String signed(String secret, String issuer, long expiresInMillis, List<String> roles) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("eve").claim("roles", roles)
                .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + expiresInMillis)).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return "Bearer " + jwt.serialize();
    }

    @Test
    void requestsWithoutATokenAreRejectedAsProblems() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Authentication required"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void forgedExpiredAndWrongIssuerTokensAreRejected() throws Exception {
        String secret = "test-secret-test-secret-test-secret-0123456789";
        mvc.perform(getAs("/api/tasks", signed(OTHER_SECRET, "task-service", 60_000, List.of("USER"))))
                .andExpect(status().isUnauthorized());                                        // wrong signing key
        mvc.perform(getAs("/api/tasks", signed(secret, "task-service", -120_000, List.of("USER"))))
                .andExpect(status().isUnauthorized());                                        // expired
        mvc.perform(getAs("/api/tasks", signed(secret, "someone-else", 60_000, List.of("USER"))))
                .andExpect(status().isUnauthorized());                                        // wrong issuer
        mvc.perform(getAs("/api/tasks", signed(secret, "task-service", 60_000, List.of("USER"))))
                .andExpect(status().isOk());                                                  // the control: valid
    }

    @Test
    void unsignedAlgNoneTokensAreRejected() throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = encoder.encodeToString(("{\"iss\":\"task-service\",\"sub\":\"eve\",\"roles\":[\"ADMIN\"],"
                + "\"exp\":" + (System.currentTimeMillis() / 1000 + 600) + "}").getBytes(StandardCharsets.UTF_8));
        mvc.perform(getAs("/api/tasks", "Bearer " + header + "." + claims + ".")).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWithoutAKnownRoleCannotUseTheApi() throws Exception {
        mvc.perform(getAs("/api/tasks", bearer("nobody", "GUEST"))).andExpect(status().isForbidden());
        mvc.perform(getAs("/api/tasks", bearer("nobody"))).andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublicButOtherActuatorEndpointsNeedAnAdministrator() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(getAs("/actuator/prometheus", user())).andExpect(status().isForbidden());
        mvc.perform(getAs("/actuator/prometheus", admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    void healthDoesNotRevealComponentDetails() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void anythingNotExplicitlyAllowedIsClosed() throws Exception {
        mvc.perform(getAs("/something-else", admin())).andExpect(status().isForbidden());
        mvc.perform(getAs("/actuator/env", admin())).andExpect(status().isNotFound());   // not even exposed
    }

    @Test
    void thereIsNoTokenEndpointOutsideTheDevProfile() throws Exception {
        mvc.perform(post("/api/dev/token").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiDocumentationIsServed() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/tasks']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(getAs("/api/tasks", user()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
