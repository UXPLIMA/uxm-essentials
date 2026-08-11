package com.uxplima.uxmessentials.security.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.view.UxmSecurityStatus;
import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.ForceReverification;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published security surface: it reports the shape of a registration and never its material, it counts a
 * lockout the way the keypad does, and both writes go in the safe direction.
 */
class SecurityApiTest {

    private static final UUID ENROLLED = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private FakeFactors repository;
    private RecordingTrusts trusts;
    private AttemptLimiter limiter;
    private ActionDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new FakeFactors();
        repository.stored.put(
                ENROLLED, new TwoFactorRegistration(ENROLLED, new TwoFactorSecret("ABCDEFGHIJKLMNOP"), true, NOW, 0L));
        trusts = new RecordingTrusts();
        limiter = new AttemptLimiter(new LockoutPolicy(3), Duration.ofMinutes(10));
        scheduler = new ActionDoubles.InlineScheduler();
    }

    @Test
    void theStatusReportsWhichFactorsExistAndNothingAboutWhatTheyAre() {
        UxmSecurityStatus status = queries().of(ENROLLED).join();

        assertThat(status).isEqualTo(new UxmSecurityStatus(ENROLLED, true, true, Optional.of(NOW), false));
        assertThat(status.enrolled()).isTrue();
    }

    @Test
    void anAccountWithNoRegistrationIsAnAnswerRatherThanAnAbsence() {
        UxmSecurityStatus status = queries().of(STRANGER).join();

        assertThat(status.enrolled()).isFalse();
        assertThat(status.enrolledAt()).isEmpty();
    }

    @Test
    void aLockedOutAccountReadsAsLockedOutFromBothTheStatusAndTheDirectQuestion() {
        spendEveryAttempt();

        assertThat(queries().isLockedOut(ENROLLED)).isTrue();
        assertThat(queries().of(ENROLLED).join().lockedOut()).isTrue();
    }

    @Test
    void forcingAVerificationForgetsEveryTrustedDeviceTheAccountHas() {
        assertThat(actions().forceVerification(ENROLLED).join()).isEqualTo(UxmOutcome.ok());

        assertThat(trusts.revoked).containsExactly(ENROLLED);
    }

    @Test
    void anAccountWithNoFactorHasNothingToBeMadeToProve() {
        UxmOutcome outcome = actions().forceVerification(STRANGER).join();

        assertThat(outcome.failure().orElseThrow().code()).isEqualTo(UxmFailure.NOT_FOUND);
        assertThat(trusts.revoked).isEmpty();
    }

    @Test
    void clearingALockoutLetsTheAccountTryAgainAndSaysSoWhenThereWasNoneToClear() {
        spendEveryAttempt();

        assertThat(actions().clearLockout(ENROLLED).join()).isEqualTo(UxmOutcome.ok());
        assertThat(limiter.isLockedOut(ENROLLED, NOW)).isFalse();
        assertThat(actions()
                        .clearLockout(ENROLLED)
                        .join()
                        .failure()
                        .orElseThrow()
                        .code())
                .isEqualTo(UxmFailure.ALREADY_IN_STATE);
    }

    /** Fail often enough that the limiter tips the account into its lockout window. */
    private void spendEveryAttempt() {
        for (int attempt = 0; attempt < 3; attempt++) {
            limiter.recordFailure(ENROLLED, NOW);
        }
    }

    private SecurityQueries queries() {
        return new SecurityQueries(repository, limiter, scheduler, CLOCK);
    }

    private SecurityActions actions() {
        return new SecurityActions(new ForceReverification(repository, trusts), limiter, scheduler);
    }

    /** The store as the verifier sees it, holding registrations and refusing anything that would move material. */
    private static final class FakeFactors implements TwoFactorRepository {

        private final Map<UUID, TwoFactorRegistration> stored = new HashMap<>();

        @Override
        public Optional<TwoFactorRegistration> find(UUID playerId) {
            return Optional.ofNullable(stored.get(playerId));
        }

        @Override
        public void enableTotp(UUID playerId, TwoFactorSecret secret) {
            throw new UnsupportedOperationException("the published surface never enrols a factor");
        }

        @Override
        public void setPin(UUID playerId, String plaintextPin) {
            throw new UnsupportedOperationException("the published surface never enrols a factor");
        }

        @Override
        public boolean verifyPin(UUID playerId, String candidate) {
            throw new UnsupportedOperationException("the published surface never checks a factor");
        }

        @Override
        public void clearTotp(UUID playerId) {
            throw new UnsupportedOperationException("the published surface never clears a factor");
        }

        @Override
        public void clearPin(UUID playerId) {
            throw new UnsupportedOperationException("the published surface never clears a factor");
        }

        @Override
        public void recordTotpStep(UUID playerId, long step) {
            throw new UnsupportedOperationException("the published surface never verifies");
        }

        @Override
        public void delete(UUID playerId) {
            throw new UnsupportedOperationException("the published surface never clears a factor");
        }
    }

    /** Remembers whose device trusts were forgotten, which is the whole of what a force does durably. */
    private static final class RecordingTrusts implements TrustStore {

        private final java.util.List<UUID> revoked = new java.util.ArrayList<>();

        @Override
        public boolean isTrusted(UUID playerId, String ipHash, Instant now) {
            return false;
        }

        @Override
        public void trust(UUID playerId, String ipHash, Instant until) {
            throw new UnsupportedOperationException("the published surface never trusts a device");
        }

        @Override
        public void revoke(UUID playerId) {
            revoked.add(playerId);
        }
    }
}
