package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.security.domain.PinPolicy;
import com.uxplima.uxmessentials.security.domain.SecretGenerator;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the PIN factor's own lifecycle across {@link SetPin}, {@link ChangePin} and {@link RemovePin}: a first PIN
 * needs no proof, replacing or removing a live one always does, and neither reaches an authenticator factor the
 * player also holds.
 */
class PinLifecycleTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final int MAX_ATTEMPTS = 3;

    private final UUID player = UUID.randomUUID();

    private FakeTwoFactorRepository repository;
    private AttemptLimiter limiter;
    private SetPin setPin;
    private ChangePin changePin;
    private RemovePin removePin;

    @BeforeEach
    void setUp() {
        repository = new FakeTwoFactorRepository(NOW);
        limiter = new AttemptLimiter(new LockoutPolicy(MAX_ATTEMPTS), Duration.ofMinutes(5));
        PinPolicy policy = new PinPolicy(4, 8);
        setPin = new SetPin(repository, policy);
        changePin = new ChangePin(repository, limiter, policy);
        removePin = new RemovePin(repository, limiter);
    }

    @Nested
    class Setting {

        @Test
        void storesAFirstPinWithoutAnyProof() {
            assertThat(setPin.set(player, "4321")).isEqualTo(PinSetResult.SET);
            assertThat(repository.verifyPin(player, "4321")).isTrue();
        }

        /**
         * The hole this closes: /pin set used to upsert straight over a live PIN, so anyone at an unlocked session
         * could take the second factor over without knowing the current one.
         */
        @Test
        void refusesToOverwriteALivePinAndLeavesTheOldOneStanding() {
            setPin.set(player, "4321");

            assertThat(setPin.set(player, "9999")).isEqualTo(PinSetResult.ALREADY_SET);
            assertThat(repository.verifyPin(player, "4321")).isTrue();
            assertThat(repository.verifyPin(player, "9999")).isFalse();
        }

        @Test
        void appliesThePolicyToAFirstPin() {
            assertThat(setPin.set(player, "12")).isEqualTo(PinSetResult.TOO_SHORT);
            assertThat(setPin.set(player, "123456789")).isEqualTo(PinSetResult.TOO_LONG);
            assertThat(setPin.set(player, "12a4")).isEqualTo(PinSetResult.NOT_NUMERIC);
            assertThat(repository.find(player)).isEmpty();
        }
    }

    @Nested
    class Changing {

        @Test
        void replacesThePinWhenTheCurrentOneIsProven() {
            setPin.set(player, "4321");

            assertThat(changePin.change(player, "4321", "8765", NOW)).isEqualTo(PinChangeResult.CHANGED);
            assertThat(repository.verifyPin(player, "8765")).isTrue();
            assertThat(repository.verifyPin(player, "4321")).isFalse();
        }

        @Test
        void refusesAWrongCurrentPinAndKeepsTheOldOne() {
            setPin.set(player, "4321");

            assertThat(changePin.change(player, "0000", "8765", NOW)).isEqualTo(PinChangeResult.INVALID_PIN);
            assertThat(repository.verifyPin(player, "4321")).isTrue();
        }

        @Test
        void refusesWhenThereIsNoPinToChange() {
            assertThat(changePin.change(player, "4321", "8765", NOW)).isEqualTo(PinChangeResult.NOT_SET);
        }

        /** The current PIN is checked first, so a wrong one never reveals whether the replacement was acceptable. */
        @Test
        void checksTheCurrentPinBeforeTheReplacementPolicy() {
            setPin.set(player, "4321");

            assertThat(changePin.change(player, "0000", "1", NOW)).isEqualTo(PinChangeResult.INVALID_PIN);
        }

        @Test
        void appliesThePolicyToTheReplacementAndKeepsTheOldPinOnRefusal() {
            setPin.set(player, "4321");

            assertThat(changePin.change(player, "4321", "1", NOW)).isEqualTo(PinChangeResult.TOO_SHORT);
            assertThat(repository.verifyPin(player, "4321")).isTrue();
        }

        @Test
        void repeatedWrongPinsLockOutTheChangePath() {
            setPin.set(player, "4321");

            for (int attempt = 0; attempt < MAX_ATTEMPTS - 1; attempt++) {
                assertThat(changePin.change(player, "0000", "8765", NOW)).isEqualTo(PinChangeResult.INVALID_PIN);
            }
            assertThat(changePin.change(player, "0000", "8765", NOW)).isEqualTo(PinChangeResult.LOCKED_OUT);

            // Once locked out even the correct current PIN is refused outright.
            assertThat(changePin.change(player, "4321", "8765", NOW)).isEqualTo(PinChangeResult.LOCKED_OUT);
            assertThat(repository.verifyPin(player, "4321")).isTrue();
        }
    }

    @Nested
    class Removing {

        @Test
        void removesThePinWhenItIsProven() {
            setPin.set(player, "4321");

            assertThat(removePin.remove(player, "4321", NOW)).isEqualTo(PinRemoveResult.REMOVED);
            assertThat(repository.find(player)).isEmpty();
        }

        @Test
        void refusesAWrongPinAndKeepsIt() {
            setPin.set(player, "4321");

            assertThat(removePin.remove(player, "0000", NOW)).isEqualTo(PinRemoveResult.INVALID_PIN);
            assertThat(repository.verifyPin(player, "4321")).isTrue();
        }

        @Test
        void refusesWhenThereIsNoPinToRemove() {
            assertThat(removePin.remove(player, "4321", NOW)).isEqualTo(PinRemoveResult.NOT_SET);
        }

        /** The mirror of the authenticator case: removing a PIN must not take an authenticator with it. */
        @Test
        void leavesAnAuthenticatorFactorStanding() {
            TwoFactorSecret secret = new SecretGenerator().generate();
            repository.enableTotp(player, secret);
            setPin.set(player, "4321");

            assertThat(removePin.remove(player, "4321", NOW)).isEqualTo(PinRemoveResult.REMOVED);
            assertThat(repository.find(player)).hasValueSatisfying(registration -> {
                assertThat(registration.pinSet()).isFalse();
                assertThat(registration.totpEnabled()).isTrue();
            });
        }

        @Test
        void repeatedWrongPinsLockOutTheRemovePath() {
            setPin.set(player, "4321");

            for (int attempt = 0; attempt < MAX_ATTEMPTS - 1; attempt++) {
                assertThat(removePin.remove(player, "0000", NOW)).isEqualTo(PinRemoveResult.INVALID_PIN);
            }
            assertThat(removePin.remove(player, "0000", NOW)).isEqualTo(PinRemoveResult.LOCKED_OUT);
            assertThat(removePin.remove(player, "4321", NOW)).isEqualTo(PinRemoveResult.LOCKED_OUT);
            assertThat(repository.verifyPin(player, "4321")).isTrue();
        }
    }

    /** The budget is one per account, not one per verb, so guesses cannot be spread across the two PIN verbs. */
    @Test
    void theChangeAndRemovePathsShareOneBruteForceBudget() {
        setPin.set(player, "4321");

        assertThat(changePin.change(player, "0000", "8765", NOW)).isEqualTo(PinChangeResult.INVALID_PIN);
        assertThat(removePin.remove(player, "0000", NOW)).isEqualTo(PinRemoveResult.INVALID_PIN);

        assertThat(removePin.remove(player, "0000", NOW)).isEqualTo(PinRemoveResult.LOCKED_OUT);
        assertThat(changePin.change(player, "4321", "8765", NOW)).isEqualTo(PinChangeResult.LOCKED_OUT);
    }
}
