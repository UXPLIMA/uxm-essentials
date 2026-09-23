package com.uxplima.uxmessentials.shared.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A particle an operator names in a setting is read with the data the server needs for it.
 *
 * <p>The vote party, the teleport arrival, the warp arrival and a kit's particle each resolved a name and drew it
 * with no data, and the server refuses a dust, a block or an item drawn that way: on a Folia 26.2 server all 22
 * such particles threw "missing required data class". The name now carries its data after a space.
 */
final class ANamedParticleCarriesItsDataTest {

    private final Location at = new Location(null, 0.5, 64, 0.5);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a dust with its colour, by key or by name, is read with that colour")
    void aColouredDustIsRead() {
        Particle.DustOptions byKey =
                (Particle.DustOptions) BukkitRegistryKeys.readParticle("minecraft:dust #00ff00", at)
                        .orElseThrow()
                        .data();
        Particle.DustOptions byName = (Particle.DustOptions) BukkitRegistryKeys.readParticle("DUST #00ff00", at)
                .orElseThrow()
                .data();

        assertThat(java.util.Objects.requireNonNull(byKey).getColor()).isEqualTo(Color.fromRGB(0x00ff00));
        assertThat(java.util.Objects.requireNonNull(byName).getColor()).isEqualTo(Color.fromRGB(0x00ff00));
    }

    @Test
    @DisplayName("a plain particle is read plain, and a name or data that cannot be read reads as nothing")
    void plainAndUnreadable() {
        assertThat(BukkitRegistryKeys.readParticle("end_rod", at).orElseThrow().particle())
                .isEqualTo(Particle.END_ROD);
        assertThat(BukkitRegistryKeys.readParticle("not_a_particle", at)).isEmpty();
        assertThat(BukkitRegistryKeys.readParticle("dust purple", at)).isEmpty();
        assertThat(BukkitRegistryKeys.readParticle("", at)).isEmpty();
    }
}
