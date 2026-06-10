package com.uxplima.uxmessentials.shared.adapter.outbound.update;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Coverage of {@link ReleaseTag} — the dependency-free extractor that pulls the version string out of an update
 * source's JSON body. Asserts {@code tag_name} is preferred, {@code version} is the fallback, whitespace around
 * the colon is tolerated, and a body missing both fields yields empty so the checker logs rather than compares
 * garbage.
 */
class ReleaseTagTest {

    @Test
    void readsGitHubTagName() {
        assertThat(ReleaseTag.from("{\"tag_name\":\"v2.7.0\",\"name\":\"Release\"}"))
                .contains("v2.7.0");
    }

    @Test
    void prefersTagNameOverVersion() {
        assertThat(ReleaseTag.from("{\"version\":\"1.0.0\",\"tag_name\":\"2.0.0\"}"))
                .contains("2.0.0");
    }

    @Test
    void fallsBackToVersionField() {
        assertThat(ReleaseTag.from("{\"name\":\"x\",\"version\":\"3.1.4\"}")).contains("3.1.4");
    }

    @Test
    void toleratesWhitespaceAroundColon() {
        assertThat(ReleaseTag.from("{ \"tag_name\" :  \"v1.2.3\" }")).contains("v1.2.3");
    }

    @Test
    void emptyWhenNeitherFieldPresent() {
        assertThat(ReleaseTag.from("{\"name\":\"Release 2.0\"}")).isEmpty();
        assertThat(ReleaseTag.from("not json at all")).isEmpty();
        assertThat(ReleaseTag.from("")).isEmpty();
    }

    @Test
    void emptyWhenTagValueIsBlank() {
        assertThat(ReleaseTag.from("{\"tag_name\":\"\"}")).isEmpty();
    }
}
