package com.thebytecodehq.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.thebytecodehq.starter.security.JwtProperties;
import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class JwtPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            .withPropertyValues("app.jwt.issuer=svc", "app.jwt.ttl=1h");

    @Test
    void theApplicationRefusesToStartWithoutASigningSecret() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void theApplicationRefusesToStartWithAWeakSecret() {
        runner.withPropertyValues("app.jwt.secret=too-short").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("at least 32 characters");
        });
    }

    @Test
    void startsWithAStrongSecret() {
        runner.withPropertyValues("app.jwt.secret=0123456789abcdef0123456789abcdef")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void validationRulesCoverEveryField() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(new JwtProperties("x".repeat(32), "svc", Duration.ofHours(1)))).isEmpty();
        assertThat(validator.validate(new JwtProperties("x".repeat(32), " ", null))).hasSize(2);
    }
}
