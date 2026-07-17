package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.security.domain.PinPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pins {@link SetPin}: a valid PIN is stored and later verifies; each policy failure returns its typed reason. */
class SetPinTest {

    private final UUID player = UUID.randomUUID();

    private FakeTwoFactorRepository repository;
    private SetPin setPin;

    @BeforeEach
    void setUp() {
        repository = new FakeTwoFactorRepository(Instant.ofEpochSecond(1_700_000_000L));
        setPin = new SetPin(repository, new PinPolicy(4, 8));
    }

    @Test
    void storesAValidPinThatThenVerifies() {
        assertThat(setPin.set(player, "1234")).isEqualTo(PinSetResult.SET);
        assertThat(repository.verifyPin(player, "1234")).isTrue();
        assertThat(repository.verifyPin(player, "9999")).isFalse();
    }

    @Test
    void refusesAPinThatIsTooShort() {
        assertThat(setPin.set(player, "12")).isEqualTo(PinSetResult.TOO_SHORT);
        assertThat(repository.find(player)).isEmpty();
    }

    @Test
    void refusesAPinThatIsTooLong() {
        assertThat(setPin.set(player, "123456789")).isEqualTo(PinSetResult.TOO_LONG);
        assertThat(repository.find(player)).isEmpty();
    }

    @Test
    void refusesANonNumericPin() {
        assertThat(setPin.set(player, "12a4")).isEqualTo(PinSetResult.NOT_NUMERIC);
        assertThat(repository.find(player)).isEmpty();
    }
}
