package com.gotham.newsmediabrowser.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the Elasticsearch client bean is created from properties (no live cluster needed).
 * A real connectivity check runs later in the P1-T03 health indicator / integration tests.
 */
class ElasticsearchClientConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropsConfig.class, ElasticsearchClientConfig.class);

    @Test
    void createsClientBeanEvenWithPlaceholderConfig() {
        runner.withPropertyValues(
                        "gotham.elasticsearch.endpoint=https://es.invalid:443",
                        "gotham.elasticsearch.api-key=placeholder")
                .run(context -> {
                    assertThat(context).hasSingleBean(ElasticsearchClient.class);
                    assertThat(context).hasNotFailed();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ElasticsearchProperties.class)
    static class PropsConfig {
    }
}
