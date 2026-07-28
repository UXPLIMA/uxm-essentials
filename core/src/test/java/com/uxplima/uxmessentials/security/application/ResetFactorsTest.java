package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.ResetFactors.ResetResult;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.domain.SecretGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /security reset}, the one path that clears a factor without proving it. The scope is honoured exactly,
 * so recovering a lost authenticator never takes a PIN the player still knows, and device trust is revoked only once
 * the target is left with nothing to verify against.
 */
class ResetFactorsTest {

    private static final Instant ENROLLED_AT = Instant.EPOCH;

    private final UUID player = UUID.randomUUID();

    private FakeTwoFactorRepository repository;
    private RecordingTrustStore trust;
    private ResetFactors reset;

    @BeforeEach
    void setUp() {
        repository = new FakeTwoFactorRepository(ENROLLED_AT);
        trust = new RecordingTrustStore();
        reset = new ResetFactors(repository, trust);
    }

    @Test
    void clearingTheAuthenticatorLeavesThePinAlone() {
        repository.enableTotp(player, new SecretGenerator().generate());
        repository.setPin(player, "4321");

        assertThat(reset.reset(player, FactorScope.TOTP)).isEqualTo(ResetResult.RESET);
        assertThat(repository.find(player)).hasValueSatisfying(registration -> {
            assertThat(registration.totpEnabled()).isFalse();
            assertThat(registration.pinSet()).isTrue();
        });
        // A factor is still standing, so the remembered devices still mean something and are left in place.
        assertThat(trust.revoked).isEmpty();
    }

    @Test
    void clearingThePinLeavesTheAuthenticatorAlone() {
        repository.enableTotp(player, new SecretGenerator().generate());
        repository.setPin(player, "4321");

        assertThat(reset.reset(player, FactorScope.PIN)).isEqualTo(ResetResult.RESET);
        assertThat(repository.find(player)).hasValueSatisfying(registration -> {
            assertThat(registration.pinSet()).isFalse();
            assertThat(registration.totpEnabled()).isTrue();
        });
        assertThat(trust.revoked).isEmpty();
    }

    @Test
    void clearingEverythingLeavesTheTargetUnenrolledAndRevokesTheirTrust() {
        repository.enableTotp(player, new SecretGenerator().generate());
        repository.setPin(player, "4321");

        assertThat(reset.reset(player, FactorScope.ALL)).isEqualTo(ResetResult.RESET);
        assertThat(repository.find(player)).isEmpty();
        assertThat(trust.revoked).containsExactly(player);
    }

    /** Clearing the last remaining factor is the same end state as ALL, so the trust goes with it. */
    @Test
    void clearingTheLastRemainingFactorAlsoRevokesTheTrust() {
        repository.setPin(player, "4321");

        assertThat(reset.reset(player, FactorScope.PIN)).isEqualTo(ResetResult.RESET);
        assertThat(repository.find(player)).isEmpty();
        assertThat(trust.revoked).containsExactly(player);
    }

    @Test
    void reportsNothingToResetForAScopeTheTargetDoesNotHold() {
        repository.setPin(player, "4321");

        assertThat(reset.reset(player, FactorScope.TOTP)).isEqualTo(ResetResult.NOTHING_TO_RESET);
        assertThat(repository.find(player)).isPresent();
        assertThat(trust.revoked).isEmpty();
    }

    @Test
    void reportsNothingToResetForAPlayerWhoHoldsNoFactor() {
        assertThat(reset.reset(player, FactorScope.ALL)).isEqualTo(ResetResult.NOTHING_TO_RESET);
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
