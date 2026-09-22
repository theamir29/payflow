package uz.payflow.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer) {

    public JwtProperties {
        // HS256 needs at least a 256-bit key; failing at startup beats issuing weak tokens.
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("payflow.jwt.secret must be at least 32 bytes long");
        }
    }
}
