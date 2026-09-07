package com.gotham.newsmediabrowser.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * P0 placeholder unit test: keeps {@code gotham-common} building green until the
 * module gains real logic (config properties and clients) in P1.
 */
class CommonModuleTest {

    @Test
    void moduleBuildsAndTestHarnessRuns() {
        assertThat(GothamCommon.MODULE_NAME).isEqualTo("gotham-common");
    }
}
