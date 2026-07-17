package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.security.domain.PinPolicy;
import com.uxplima.uxmessentials.security.domain.SecretGenerator;
import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link DisableTwoFactor}: removal needs a current factor, either a TOTP code or a PIN proves one, and the shared
 * {@link AttemptLimiter} rate-limits the path so it cannot be spammed to brute-force the PIN.
 */
class DisableTwoFactorTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final int WINDOW = 1;
    private static final int MAX_ATTEMPTS = 3;

    private final UUID player = UUID.randomUUID();

    private FakeTwoFactorRepository repository;
    private AttemptLimiter limiter;
    private DisableTwoFactor disable;

    @BeforeEach
    void setUp() {
        repository = new FakeTwoFactorRepository(NOW);
        limiter = new AttemptLimiter(new LockoutPolicy(MAX_ATTEMPTS), Duration.ofMinutes(5));
        disable = new DisableTwoFactor(repository, limiter, WINDOW);
    }

    @Test
    void refusesWhenThePlayerHoldsNoFactor() {
        assertThat(disable.disable(player, "000000", NOW)).isEqualTo(DisableResult.NOT_ENROLLED);
    }

    @Test
    void refusesAWrongCodeAndKeepsTheFactor() {
        TwoFactorSecret secret = new SecretGenerator().generate();
        repository.enableTotp(player, secret);

        assertThat(disable.disable(player, "000000", NOW)).isEqualTo(DisableResult.INVALID_FACTOR);
        assertThat(repository.find(player)).isPresent();
    }

    @Test
    void removesTheRegistrationWhenAValidCodeProvesTheFactor() {
        TwoFactorSecret secret = new SecretGenerator().generate();
        repository.enableTotp(player, secret);
        String code = TotpCode.generate(secret, NOW);

        assertThat(disable.disable(player, code, NOW)).isEqualTo(DisableResult.DISABLED);
        assertThat(repository.find(player)).isEmpty();
    }

    @Test
    void aPinAlsoProvesTheFactorForRemoval() {
        new SetPin(repository, new PinPolicy(4, 8)).set(player, "4321");

        assertThat(disable.disable(player, "4321", NOW)).isEqualTo(DisableResult.DISABLED);
        assertThat(repository.find(player)).isEmpty();
    }

    @Test
    void repeatedWrongPinsLockOutTheDisablePathAndStopRemovingTheFactor() {
        new SetPin(repository, new PinPolicy(4, 8)).set(player, "4321");

        // The wrong guesses count against the shared budget; the maxAttempts-th tips the account into a lockout.
        for (int attempt = 0; attempt < MAX_ATTEMPTS - 1; attempt++) {
            assertThat(disable.disable(player, "0000", NOW)).isEqualTo(DisableResult.INVALID_FACTOR);
        }
        assertThat(disable.disable(player, "0000", NOW)).isEqualTo(DisableResult.LOCKED_OUT);

        // Once locked out, even the correct PIN is refused outright — the factor is not removed.
        assertThat(disable.disable(player, "4321", NOW)).isEqualTo(DisableResult.LOCKED_OUT);
        assertThat(repository.find(player)).isPresent();
    }

    @Test
    void aLockedOutAccountFromAnotherSurfaceCannotDisable() {
        new SetPin(repository, new PinPolicy(4, 8)).set(player, "4321");
        // A lockout accrued on any surface (here simulated directly on the shared limiter) blocks the disable path.
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            limiter.recordFailure(player, NOW);
        }

        assertThat(disable.disable(player, "4321", NOW)).isEqualTo(DisableResult.LOCKED_OUT);
        assertThat(repository.find(player)).isPresent();
    }
}
