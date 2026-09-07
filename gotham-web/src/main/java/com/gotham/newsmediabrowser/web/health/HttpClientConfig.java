package com.gotham.newsmediabrowser.web.health;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a shared {@link HttpClient} with a bounded connect timeout for lightweight
 * outbound checks (e.g. the ImageBind health probe).
 */
@Configuration(proxyBeanMethods = false)
public class HttpClientConfig {

    @Bean
    HttpClient healthHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }
}
