package com.gotham.newsmediabrowser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Gotham News &amp; Media Browser web application.
 *
 * <p>The application lives in the root package {@code com.gotham.newsmediabrowser} so that
 * component scanning reaches beans declared in the {@code gotham-common} module (a separate
 * JAR under the same base package) without extra configuration.
 *
 * <p>{@link ConfigurationPropertiesScan} registers the {@code @ConfigurationProperties} records
 * from {@code gotham-common} (Elasticsearch / GCS / ImageBind / media limits).
 */
@SpringBootApplication(scanBasePackages = "com.gotham.newsmediabrowser")
@ConfigurationPropertiesScan("com.gotham.newsmediabrowser")
public class GothamMediaBrowserApplication {

    public static void main(String[] args) {
        SpringApplication.run(GothamMediaBrowserApplication.class, args);
    }
}
