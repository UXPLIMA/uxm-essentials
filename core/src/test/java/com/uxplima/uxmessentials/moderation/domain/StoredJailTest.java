package com.uxplima.uxmessentials.moderation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * {@link StoredJail}: the DB-backed jail value object. The name is normalised to its canonical lowercase form
 * so {@code /setjail Spawn} and {@code /jail <player> spawn} address the same jail (matching the config seam's
 * case-folding), and a blank or overlong name is rejected so an invalid one never reaches the store. The
 * captured position is kept verbatim.
 */
class StoredJailTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = new Position(WORLD, 10.5, 64, 20.5, 90f, 0f);

    @Test
    void normalisesTheNameToLowercaseAndTrimsIt() {
        StoredJail jail = StoredJail.of("  SpawnJail  ", AT);

        assertThat(jail.name()).isEqualTo("spawnjail");
        assertThat(jail.location()).isEqualTo(AT);
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> StoredJail.of("   ", AT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnOverlongName() {
        String tooLong = "j".repeat(StoredJail.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> StoredJail.of(tooLong, AT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsAMaximumLengthName() {
        String exact = "j".repeat(StoredJail.MAX_NAME_LENGTH);

        assertThat(StoredJail.of(exact, AT).name()).isEqualTo(exact);
    }

    @Test
    void normaliseFoldsCaseWithoutBuildingAJail() {
        assertThat(StoredJail.normalise("MINES")).isEqualTo("mines");
    }
}
