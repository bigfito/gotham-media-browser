package com.gotham.newsmediabrowser;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * P0 smoke test: the Spring application context starts with the committed
 * placeholder configuration (no Elasticsearch/GCS/ImageBind beans wired yet).
 */
@SpringBootTest
class GothamMediaBrowserApplicationTests {

    @Test
    void contextLoads() {
        // Fails if the application context cannot be built.
    }
}
