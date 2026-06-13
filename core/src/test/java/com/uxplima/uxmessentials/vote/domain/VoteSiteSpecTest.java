package com.uxplima.uxmessentials.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class VoteSiteSpecTest {

    @Test
    void threeArgConstructorDefaultsServiceToName() {
        VoteSiteSpec spec = new VoteSiteSpec("PlanetMinecraft", Optional.empty(), Duration.ofHours(24));
        assertThat(spec.name()).isEqualTo("PlanetMinecraft");
        assertThat(spec.service()).isEqualTo("PlanetMinecraft");
    }

    @Test
    void fourArgConstructorKeepsServiceSeparateFromName() {
        VoteSiteSpec spec = new VoteSiteSpec("PlanetMinecraft", "PMC", Optional.empty(), Duration.ofHours(24));
        assertThat(spec.name()).isEqualTo("PlanetMinecraft");
        assertThat(spec.service()).isEqualTo("PMC");
    }

    @Test
    void blankServiceIsRejected() {
        assertThatThrownBy(() -> new VoteSiteSpec("PlanetMinecraft", "  ", Optional.empty(), Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> new VoteSiteSpec(" ", Optional.empty(), Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
