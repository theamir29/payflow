package uz.payflow.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Injected instead of calling {@code Instant.now()} so that tests can pin the time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
