package uz.payflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    /** Adds the "Authorize" button to Swagger UI so every endpoint can be tried with a token. */
    @Bean
    OpenAPI payflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayFlow API")
                        .version("1.0.0")
                        .description("""
                                Digital wallet: accounts in UZS and USD, deposits, transfers with \
                                idempotency keys, operation history and monthly statistics.

                                Get a token from `POST /api/auth/login` (demo: `demo@payflow.uz` / `demo12345`) \
                                and paste it into **Authorize**."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                // Relative server: "Try it out" calls whichever host served the page, including the
                // Vercel proxy in front of the API.
                .servers(List.of(new Server().url("/")));
    }
}
