package com.gotham.newsmediabrowser.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Elasticsearch connection settings (Elastic Cloud Serverless).
 *
 * <p>Bound from {@code gotham.elasticsearch.*}. The committed {@code application.properties}
 * holds placeholders; real values come from the untracked {@code application-local.properties}
 * (or environment variables). The API key is a secret — {@link #toString()} masks it so it can
 * never leak into logs or diagnostics.
 *
 * @param endpoint HTTPS endpoint, e.g. {@code https://xxx.es.<region>.gcp.elastic.cloud:443}
 * @param apiKey   Base64 API key (secret)
 */
@ConfigurationProperties(prefix = "gotham.elasticsearch")
public record ElasticsearchProperties(String endpoint, String apiKey) {

    /** True when both endpoint and API key look configured (not blank, not a placeholder). */
    public boolean isConfigured() {
        return isReal(endpoint) && isReal(apiKey);
    }

    private static boolean isReal(String value) {
        return value != null && !value.isBlank() && !value.startsWith("YOUR");
    }

    @Override
    public String toString() {
        return "ElasticsearchProperties[endpoint=%s, apiKey=%s]".formatted(endpoint, maskedApiKey());
    }

    /** Never expose the raw key; only whether it is present and its length. */
    private String maskedApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return "<unset>";
        }
        return "****(" + apiKey.length() + " chars)";
    }
}
