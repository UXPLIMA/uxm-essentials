package com.uxplima.uxmessentials.security.application;

import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.security.domain.SecretGenerator;
import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import com.uxplima.uxmessentials.testing.MemoryAttemptStore;
import org.junit.jupiter.api.Test;

/**
 * A proof never tells a right authenticator code from a wrong one by failing differently. Whatever can fail is read
 * before any comparison, so it fails alike for every code and the attempt is given back; a failure after a right code
 * counts as a wrong one, never as "try again".
 */
class ProofOracleTest {

    private static final UUID ADA = new UUID(0L, 7L);

    private final TwoFactorSecret secret = new SecretGenerator().generate();
    private final MemoryAttemptStore attempts = new MemoryAttemptStore();
    private final AttemptLimiter limiter =
            new AttemptLimiter(attempts, player -> {}, new LockoutPolicy(3), Duration.ofMinutes(5));

    @Test
    void aDamagedPinFailsTheSameForARightAndAWrongCodeAndSpendsNothing() {
        Store store = new Store(secret);
        store.pinUnreadable = true;
        VerifyTwoFactor verify = new VerifyTwoFactor(store, 1);

        for (String code : new String[] {TotpCode.generate(secret, NOW), "000000", "111111"}) {
            assertThatThrownBy(() -> check(verify, code)).isInstanceOf(IllegalStateException.class);
        }

        assertThat(attempts.addFailure(ADA))
                .as("nothing was spent before this one")
                .isEqualTo(1);
    }

    @Test
    void aRightCodeWhoseStepCannotBeRecordedCountsAsAWrongOne() {
        Store store = new Store(secret);
        store.stepUnwritable = true;
        VerifyTwoFactor verify = new VerifyTwoFactor(store, 1);

        AttemptLimiter.Checked right = check(verify, TotpCode.generate(secret, NOW));
        AttemptLimiter.Checked wrong = check(verify, "000000");

        assertThat(right).isEqualTo(new AttemptLimiter.Checked(AttemptLimiter.Verdict.WRONG, 2));
        assertThat(wrong).isEqualTo(new AttemptLimiter.Checked(AttemptLimiter.Verdict.WRONG, 1));
    }

    @Test
    void aRightCodePassesAndIsSpent() {
        VerifyTwoFactor verify = new VerifyTwoFactor(new Store(secret), 1);
        String code = TotpCode.generate(secret, NOW);

        assertThat(check(verify, code)).isEqualTo(AttemptLimiter.Checked.PASSED);
    }

    private AttemptLimiter.Checked check(VerifyTwoFactor verify, String code) {
        return limiter.check(ADA, NOW, () -> {
            VerifyTwoFactor.Prepared prepared = verify.prepare(ADA);
            return () -> prepared.verify(code, NOW) != VerifyResult.INVALID;
        });
    }

    /** One player with an authenticator and a PIN; reading the PIN or writing the spent step can be made to fail. */
    private static final class Store implements TwoFactorRepository {

        private final TwoFactorSecret secret;
        boolean pinUnreadable;
        boolean stepUnwritable;
        private long lastStep;

        Store(TwoFactorSecret secret) {
            this.secret = secret;
        }

        @Override
        public Optional<TwoFactorRegistration> find(UUID playerId) {
            return Optional.of(new TwoFactorRegistration(playerId, secret, true, NOW, lastStep));
        }

        @Override
        public PinCheck pinCheck(UUID playerId) {
            if (pinUnreadable) {
                throw new IllegalStateException("stored pin_hash is not in the expected form");
            }
            return candidate -> candidate.equals("4821");
        }

        @Override
        public void recordTotpStep(UUID playerId, long step) {
            if (stepUnwritable) {
                throw new IllegalStateException("database down");
            }
            lastStep = Math.max(lastStep, step);
        }

        @Override
        public void enableTotp(UUID playerId, TwoFactorSecret secret) {
            throw new AssertionError("not used");
        }

        @Override
        public void setPin(UUID playerId, String plaintextPin) {
            throw new AssertionError("not used");
        }

        @Override
        public void clearTotp(UUID playerId) {
            throw new AssertionError("not used");
        }

        @Override
        public void clearPin(UUID playerId) {
            throw new AssertionError("not used");
        }

        @Override
        public void delete(UUID playerId) {
            throw new AssertionError("not used");
        }
    }
}
