package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class WarpEffectsTest {

    @Test
    void noneLeavesEveryFlourishAbsent() {
        WarpEffects effects = WarpEffects.none();

        assertThat(effects.departureSound()).isEmpty();
        assertThat(effects.arrivalSound()).isEmpty();
        assertThat(effects.departureParticle()).isEmpty();
        assertThat(effects.arrivalParticle()).isEmpty();
    }

    @Test
    void keepsTheTokensItIsGiven() {
        WarpEffects effects = new WarpEffects(
                Optional.of("entity.enderman.teleport"),
                Optional.of("entity.player.levelup"),
                Optional.of("PORTAL"),
                Optional.of("END_ROD"));

        assertThat(effects.departureSound()).contains("entity.enderman.teleport");
        assertThat(effects.arrivalParticle()).contains("END_ROD");
    }

    @Test
    @SuppressWarnings("NullAway") // verifies the compact constructor rejects a literal null Optional
    void rejectsANullOptional() {
        assertThatNullPointerException()
                .isThrownBy(() -> new WarpEffects(null, Optional.empty(), Optional.empty(), Optional.empty()));
    }
}
