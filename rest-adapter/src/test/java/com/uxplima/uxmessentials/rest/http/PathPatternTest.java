package com.uxplima.uxmessentials.rest.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PathPatternTest {

    @Test
    void aLiteralPathMatchesOnlyItself() {
        PathPattern pattern = PathPattern.of("/api/v1/warps");

        assertThat(pattern.match("/api/v1/warps")).contains(Map.of());
        assertThat(pattern.match("/api/v1/homes")).isEmpty();
    }

    @Test
    void aParameterCapturesTheSegmentItStandsFor() {
        PathPattern pattern = PathPattern.of("/api/v1/players/{uuid}/homes");

        assertThat(pattern.match("/api/v1/players/abc/homes")).contains(Map.of("uuid", "abc"));
    }

    @Test
    void aParameterDoesNotSwallowASlash() {
        PathPattern pattern = PathPattern.of("/api/v1/players/{uuid}/homes");

        assertThat(pattern.match("/api/v1/players/one/two/homes")).isEmpty();
    }

    @Test
    void aTrailingSlashIsTheSamePath() {
        assertThat(PathPattern.of("/api/v1/warps").match("/api/v1/warps/")).isPresent();
    }

    @Test
    void aShorterOrLongerPathIsADifferentPath() {
        PathPattern pattern = PathPattern.of("/api/v1/warps/{name}");

        assertThat(pattern.match("/api/v1/warps")).isEmpty();
        assertThat(pattern.match("/api/v1/warps/spawn/extra")).isEmpty();
    }

    @Test
    void aPathMustStartWithASlash() {
        assertThatThrownBy(() -> PathPattern.of("api/v1/warps")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePatternPrintsAsItWasWritten() {
        assertThat(PathPattern.of("/api/v1/players/{uuid}").source()).isEqualTo("/api/v1/players/{uuid}");
    }
}
