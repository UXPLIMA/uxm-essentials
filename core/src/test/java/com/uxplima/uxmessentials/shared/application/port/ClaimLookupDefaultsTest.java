package com.uxplima.uxmessentials.shared.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link ClaimLookup} ownership defaults in isolation: a provider that predates the
 * ownership methods (overriding only {@code isTrusted}) must keep working, while a provider that
 * answers ownership directly must be able to override them.
 */
class ClaimLookupDefaultsTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    @Test
    void legacyLookup_isOwner_mirrorsIsTrusted_andOwnerIsEmpty() {
        ClaimLookup lookup = new TrustOnlyLookup(OWNER);

        assertThat(lookup.isOwner(OWNER)).isEqualTo(lookup.isTrusted(OWNER)).isTrue();
        assertThat(lookup.isOwner(STRANGER))
                .isEqualTo(lookup.isTrusted(STRANGER))
                .isFalse();
        assertThat(lookup.owner()).isEmpty();
    }

    @Test
    void ownershipAwareLookup_reportsItsOwnAnswers() {
        ClaimLookup lookup = new OwnerAwareLookup(OWNER);

        // Trusted-but-not-owner proves isOwner is narrower than isTrusted here.
        assertThat(lookup.isTrusted(STRANGER)).isTrue();
        assertThat(lookup.isOwner(STRANGER)).isFalse();
        assertThat(lookup.isOwner(OWNER)).isTrue();
        assertThat(lookup.owner()).contains(OWNER);
    }

    @SuppressWarnings("NullAway") // deliberately feeds null to verify the default rejects it at entry
    @Test
    void defaultIsOwner_rejectsNullPlayer() {
        ClaimLookup lookup = new TrustOnlyLookup(OWNER);

        assertThatNullPointerException().isThrownBy(() -> lookup.isOwner(null));
    }

    /** Overrides only the two legacy methods, exercising the ownership defaults. */
    private static final class TrustOnlyLookup implements ClaimLookup {

        private final UUID owner;

        TrustOnlyLookup(UUID owner) {
            this.owner = owner;
        }

        @Override
        public boolean isTrusted(UUID player) {
            return owner.equals(player);
        }

        @Override
        public boolean isBanned(UUID player) {
            return false;
        }
    }

    /** Overrides all four methods so its own ownership answers, not the defaults, take effect. */
    private static final class OwnerAwareLookup implements ClaimLookup {

        private final UUID owner;

        OwnerAwareLookup(UUID owner) {
            this.owner = owner;
        }

        @Override
        public boolean isTrusted(UUID player) {
            return true;
        }

        @Override
        public boolean isBanned(UUID player) {
            return false;
        }

        @Override
        public boolean isOwner(UUID player) {
            return owner.equals(player);
        }

        @Override
        public Optional<UUID> owner() {
            return Optional.of(owner);
        }
    }
}
