package com.gotham.newsmediabrowser.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GothamExceptionTest {

    @Test
    void notFoundMapsTo404() {
        NotFoundException ex = new NotFoundException("Article abc was not found.");
        assertThat(ex.getHttpStatus()).isEqualTo(404);
        assertThat(ex.getUserReason()).isEqualTo("Article abc was not found.");
    }

    @Test
    void dependencyMapsTo503AndNamesService() {
        DependencyException ex = new DependencyException("Elasticsearch", new RuntimeException("boom"));
        assertThat(ex.getHttpStatus()).isEqualTo(503);
        assertThat(ex.getServiceName()).isEqualTo("Elasticsearch");
        assertThat(ex.getUserReason()).contains("Elasticsearch").contains("unavailable");
        // The user-safe reason must not leak the underlying cause message.
        assertThat(ex.getUserReason()).doesNotContain("boom");
    }

    @Test
    void mediaLimitMapsTo413() {
        MediaLimitException ex = new MediaLimitException("Video exceeds 50 MiB.");
        assertThat(ex.getHttpStatus()).isEqualTo(413);
        assertThat(ex.getUserReason()).isEqualTo("Video exceeds 50 MiB.");
    }
}
