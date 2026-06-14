package com.uxplima.uxmessentials.vaults.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The {@code Vault} presentation transitions: {@link Vault#renamedTo} and {@link Vault#iconSet} each return a
 * copy that changes only the targeted column and preserves everything else, a {@code null} argument clears the
 * column, and a name beyond the domain sanity cap is rejected as a programming error.
 */
class VaultTest {

    private static final Instant AT = Instant.parse("2026-06-14T00:00:00Z");

    private static Vault vault() {
        return Vault.allocate(new VaultId(UUID.randomUUID(), 1), new VaultSize(3), AT);
    }

    @Test
    void renamedToSetsTheDisplayNameAndPreservesEverythingElse() {
        Vault original = vault().iconSet("CHEST");

        Vault renamed = original.renamedTo("Loot");

        assertThat(renamed.displayName()).isEqualTo("Loot");
        assertThat(renamed.iconMaterial()).isEqualTo("CHEST"); // icon preserved
        assertThat(renamed.size()).isEqualTo(original.size());
        assertThat(renamed.contents()).isEqualTo(original.contents());
        assertThat(renamed.lastTouched()).isEqualTo(original.lastTouched());
        assertThat(original.displayName()).isNull(); // original untouched (immutable)
    }

    @Test
    void renamedToWithNullClearsTheDisplayName() {
        Vault named = vault().renamedTo("Loot");

        assertThat(named.renamedTo(null).displayName()).isNull();
    }

    @Test
    void renamedToRejectsANameBeyondTheSanityCap() {
        String tooLong = "x".repeat(257);

        assertThatThrownBy(() -> vault().renamedTo(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renamedToAcceptsANameExactlyAtTheSanityCap() {
        String maxName = "x".repeat(256);

        assertThat(vault().renamedTo(maxName).displayName()).isEqualTo(maxName);
    }

    @Test
    void iconSetSetsTheIconAndPreservesEverythingElse() {
        Vault original = vault().renamedTo("Loot");

        Vault iconed = original.iconSet("ENDER_CHEST");

        assertThat(iconed.iconMaterial()).isEqualTo("ENDER_CHEST");
        assertThat(iconed.displayName()).isEqualTo("Loot"); // name preserved
        assertThat(iconed.size()).isEqualTo(original.size());
        assertThat(iconed.contents()).isEqualTo(original.contents());
        assertThat(iconed.lastTouched()).isEqualTo(original.lastTouched());
        assertThat(original.iconMaterial()).isNull(); // original untouched (immutable)
    }

    @Test
    void iconSetWithNullClearsTheIcon() {
        Vault iconed = vault().iconSet("ENDER_CHEST");

        assertThat(iconed.iconSet(null).iconMaterial()).isNull();
    }

    @Test
    void allocateStartsWithNoNameOrIcon() {
        Vault fresh = vault();

        assertThat(fresh.displayName()).isNull();
        assertThat(fresh.iconMaterial()).isNull();
    }
}
