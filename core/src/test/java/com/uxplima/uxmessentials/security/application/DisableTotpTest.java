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
 * Pins {@link DisableTotp}: removal needs a current code from the same authenticator, a PIN neither proves it nor is
 * taken by it, and the shared {@link AttemptLimiter} rate-limits the path so it cannot be spammed.
 */
class DisableTotpTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final int WINDOW = 1;
    private static final int MAX_ATTEMPTS = 3;

    private final UUID player = UUID.randomUUID();

    private FakeTwoFactorRepository repository;
    private AttemptLimiter limiter;
    private DisableTotp disable;

    @BeforeEach
    void setUp() {
        repository = new FakeTwoFactorRepository(NOW);
        limiter = new AttemptLimiter(new LockoutPolicy(MAX_ATTEMPTS), Duration.ofMinutes(5));
        disable = new DisableTotp(repository, limiter, WINDOW);
    }

    @Test
    void refusesWhenThePlayerHoldsNoFactor() {
        assertThat(disable.disable(player, "000000", NOW)).isEqualTo(DisableResult.NOT_ENROLLED);
    }

    @Test
    void refusesAWrongCodeAndKeepsTheFactor() {
        TwoFactorSecret secret = new SecretGenerator().generate();
        repository.enableTotp(player, secret);

        assertThat(disable.disable(player, "000000", NOW)).isEqualTo(DisableResult.INVALID_CODE);
        assertThat(repository.find(player)).isPresent();
    }

    @Test
    void removesTheAuthenticatorWhenAValidCodeProvesIt() {
        TwoFactorSecret secret = new SecretGenerator().generate();
        repository.enableTotp(player, secret);
        String code = TotpCode.generate(secret, NOW);

        assertThat(disable.disable(player, code, NOW)).isEqualTo(DisableResult.DISABLED);
        assertThat(repository.find(player)).isEmpty();
    }

    /** The separation that makes the two factors independent: a PIN is not a key to the authenticator. */
    @Test
    void aPinNeitherProvesNorIsTakenByTheAuthenticatorRemoval() {
        TwoFactorSecret secret = new SecretGenerator().generate();
        repository.enableTotp(player, secret);
        new SetPin(repository, new PinPolicy(4, 8)).set(player, "4321");

        assertThat(disable.disable(player, "4321", NOW)).isEqualTo(DisableResult.INVALID_CODE);

        // The correct code removes only the authenticator; the PIN survives as a factor in its own right.
        assertThat(disable.disable(player, TotpCode.generate(secret, NOW), NOW)).isEqualTo(DisableResult.DISABLED);
        assertThat(repository.find(player)).hasValueSatisfying(registration -> {
            assertThat(registration.totpEnabled()).isFalse();
            assertThat(registration.pinSet()).isTrue();
        });
    }

    /** A player holding only a PIN has no authenticator to disable, whatever they submit here. */
    @Test
    void refusesWhenOnlyAPinIsHeld() {
        new SetPin(repository, new PinPolicy(4, 8)).set(player, "4321");

        assertThat(disable.disable(player, "4321", NOW)).isEqualTo(DisableResult.NOT_ENROLLED);
        assertThat(repository.find(player)).isPresent();
    }

    @Test
    void repeatedWrongCodesLockOutTheDisablePathAndStopRemovingTheFactor() {
        TwoFactorSecret secret = new SecretGenerator().generate();
        repository.enableTotp(player, secret);

        // The wrong guesses count against the shared budget; the maxAttempts-th tips the account into a lockout.
        for (int attempt = 0; attempt < MAX_ATTEMPTS - 1; attempt++) {
            assertThat(disable.disable(player, "000000", NOW)).isEqualTo(DisableResult.INVALID_CODE);
        }
        assertThat(disable.disable(player, "000000", NOW)).isEqualTo(DisableResult.LOCKED_OUT);

        // Once locked out, even the correct code is refused outright and the factor is not removed.
        assertThat(disable.disable(player, TotpCode.generate(secret, NOW), NOW)).isEqualTo(DisableResult.LOCKED_OUT);
        assertThat(repository.find(player)).isPresent();
    }

    @Test
    void aLockedOutAccountFromAnotherSurfaceCannotDisable() {
        TwoFactorSecret secret = new SecretGenerator().generate();
        repository.enableTotp(player, secret);
        // A lockout accrued on any surface (here simulated directly on the shared limiter) blocks the disable path.
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            limiter.recordFailure(player, NOW);
        }

        assertThat(disable.disable(player, TotpCode.generate(secret, NOW), NOW)).isEqualTo(DisableResult.LOCKED_OUT);
        assertThat(repository.find(player)).isPresent();
    }
}
