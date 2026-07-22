package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TrustStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the durable half of {@code /2fa force}: an enrolled target has every device trust revoked (so their next join
 * re-verifies even from a trusted device) and is reported FORCED; a target with no factor has nothing revoked and is
 * reported NOT_ENROLLED, since there is nothing to verify against.
 */
class ForceReverificationTest {

    private static final Instant ENROLLED_AT = Instant.EPOCH;

    @Test
    void forcingAnEnrolledPlayerRevokesEveryDeviceTrust() {
        UUID player = UUID.randomUUID();
        FakeTwoFactorRepository repository = new FakeTwoFactorRepository(ENROLLED_AT);
        repository.setPin(player, "1234"); // enrolled with a PIN factor
        RecordingTrustStore trust = new RecordingTrustStore();

        ForceReverification.ForceResult result = new ForceReverification(repository, trust).force(player);

        assertThat(result).isEqualTo(ForceReverification.ForceResult.FORCED);
        assertThat(trust.revoked).containsExactly(player);
    }

    @Test
    void forcingANonEnrolledPlayerRevokesNothing() {
        UUID player = UUID.randomUUID();
        RecordingTrustStore trust = new RecordingTrustStore();

        ForceReverification.ForceResult result =
                new ForceReverification(new FakeTwoFactorRepository(ENROLLED_AT), trust).force(player);

        assertThat(result).isEqualTo(ForceReverification.ForceResult.NOT_ENROLLED);
        assertThat(trust.revoked).isEmpty();
    }

    /** Records which players had their device trust revoked. */
    private static final class RecordingTrustStore implements TrustStore {
        private final Set<UUID> revoked = new HashSet<>();

        @Override
        public boolean isTrusted(UUID playerId, String ipHash, Instant now) {
            return false;
        }

        @Override
        public void trust(UUID playerId, String ipHash, Instant until) {
            // not exercised here
        }

        @Override
        public void revoke(UUID playerId) {
            revoked.add(playerId);
        }
    }
}
