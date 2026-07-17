package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.security.domain.PinPolicy;
import com.uxplima.uxmessentials.security.domain.SecretGenerator;
import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pins {@link DisableTwoFactor}: removal needs a current factor, and either a TOTP code or a PIN proves one. */
class DisableTwoFactorTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final int WINDOW = 1;

    private final UUID player = UUID.randomUUID();

    private FakeTwoFactorRepository repository;
    private DisableTwoFactor disable;

    @BeforeEach
    void setUp() {
        repository = new FakeTwoFactorRepository(NOW);
        disable = new DisableTwoFactor(repository, WINDOW);
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
}
