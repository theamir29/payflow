package uz.payflow.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param zone time zone used to cut statistics into calendar months
 */
@ConfigurationProperties(prefix = "payflow")
public record PayflowProperties(ZoneId zone) {
}
