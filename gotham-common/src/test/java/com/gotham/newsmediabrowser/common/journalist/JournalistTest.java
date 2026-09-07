package com.gotham.newsmediabrowser.common.journalist;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the journalist domain model, focused on the derived full name. */
class JournalistTest {

    @Test
    void fullNameJoinsFirstAndLastWithASingleSpace() {
        Journalist journalist = Journalist.newJournalist("Vicki", "Vale", "vicki@gazette.gotham", "Reporter");
        assertThat(journalist.fullName()).isEqualTo("Vicki Vale");
    }

    @Test
    void fullNameTrimsAndSkipsBlankParts() {
        assertThat(Journalist.newJournalist("  Vicki  ", "  ", null, null).fullName()).isEqualTo("Vicki");
        assertThat(Journalist.newJournalist(null, "Vale", null, null).fullName()).isEqualTo("Vale");
        assertThat(Journalist.newJournalist(" ", " ", null, null).fullName()).isEmpty();
    }

    @Test
    void newJournalistHasNoIdOrTimestampsUntilPersisted() {
        Journalist journalist = Journalist.newJournalist("Clark", "Kent", null, null);
        assertThat(journalist.id()).isNull();
        assertThat(journalist.createdAt()).isNull();
        assertThat(journalist.updatedAt()).isNull();
    }

    @Test
    void withIdReturnsACopyLeavingTheOriginalUnchanged() {
        Journalist original = Journalist.newJournalist("Clark", "Kent", null, null);
        Journalist stored = original.withId("abc123");
        assertThat(stored.id()).isEqualTo("abc123");
        assertThat(original.id()).isNull();
    }
}
