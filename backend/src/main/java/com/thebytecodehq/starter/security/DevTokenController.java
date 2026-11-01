package com.thebytecodehq.starter.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mints a token for local development so the UI and Swagger work without an identity provider.
 * It exists only under the dev profile (and never alongside prod), so it is not part of a production build's surface.
 */
@RestController
@Profile("dev & !prod")
@RequestMapping("/api/dev")
public class DevTokenController {

    public record TokenRequest(
            @NotBlank @Size(max = 60) String username,
            List<@Pattern(regexp = "USER|ADMIN") String> roles) {
    }

    public record TokenResponse(String accessToken, long expiresIn) {
    }

    private final JwtService jwt;

    public DevTokenController(JwtService jwt) {
        this.jwt = jwt;
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        List<String> roles = request.roles() == null || request.roles().isEmpty() ? List.of("USER") : request.roles();
        return new TokenResponse(jwt.mint(request.username().strip(), roles), jwt.ttlSeconds());
    }
}
