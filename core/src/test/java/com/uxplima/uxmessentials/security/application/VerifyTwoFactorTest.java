package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link VerifyTwoFactor}: a correct PIN or a correct TOTP code passes, a wrong value is INVALID, and a player
 * with no factor is NOT_ENROLLED: the three outcomes the join-verification keypad branches on. An authenticator
 * code is also single-use: the step it matched is spent, so presenting the same six digits again fails.
 */
class VerifyTwoFactorTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);

    private final UUID player = UUID.randomUUID();
    private final FakeTwoFactorRepository repository = new FakeTwoFactorRepository(NOW);
    private final VerifyTwoFactor verify = new VerifyTwoFactor(repository, 1);

    @Test
    void reportsNotEnrolledWhenThePlayerHoldsNoFactor() {
        assertThat(verify.verify(player, "1234", NOW)).isEqualTo(VerifyResult.NOT_ENROLLED);
    }

    @Test
    void acceptsTheCorrectPinAndRejectsAWrongOne() {
        repository.setPin(player, "1234");

        assertThat(verify.verify(player, "1234", NOW)).isEqualTo(VerifyResult.SUCCESS);
        assertThat(verify.verify(player, "9999", NOW)).isEqualTo(VerifyResult.INVALID);
    }

    @Test
    void acceptsAValidTotpCodeForTheCurrentTimeStep() {
        TwoFactorSecret secret = new TwoFactorSecret("JBSWY3DPEHPK3PXP");
        repository.enableTotp(player, secret);
        String code = TotpCode.generate(secret, NOW);

        assertThat(verify.verify(player, code, NOW)).isEqualTo(VerifyResult.SUCCESS);
        assertThat(verify.verify(player, "000000", NOW)).isEqualTo(VerifyResult.INVALID);
    }

    @Test
    void refusesAnAuthenticatorCodeThatWasAlreadyUsed() {
        TwoFactorSecret secret = new TwoFactorSecret("JBSWY3DPEHPK3PXP");
        repository.enableTotp(player, secret);
        String code = TotpCode.generate(secret, NOW);

        assertThat(verify.verify(player, code, NOW)).isEqualTo(VerifyResult.SUCCESS);

        // The same digits, still inside their 30-second window: whoever read them over the player's shoulder gets
        // nothing, and the replay counts as an ordinary failure.
        assertThat(verify.verify(player, code, NOW)).isEqualTo(VerifyResult.INVALID);
        assertThat(verify.verify(player, code, NOW.plusSeconds(20))).isEqualTo(VerifyResult.INVALID);
    }

    @Test
    void acceptsTheNextCodeOnceTheStepRollsOver() {
        TwoFactorSecret secret = new TwoFactorSecret("JBSWY3DPEHPK3PXP");
        repository.enableTotp(player, secret);
        Instant later = NOW.plusSeconds(60);

        assertThat(verify.verify(player, TotpCode.generate(secret, NOW), NOW)).isEqualTo(VerifyResult.SUCCESS);
        assertThat(verify.verify(player, TotpCode.generate(secret, later), later))
                .isEqualTo(VerifyResult.SUCCESS);
    }

    @Test
    void aSpentCodeDoesNotStopThePinFromWorking() {
        TwoFactorSecret secret = new TwoFactorSecret("JBSWY3DPEHPK3PXP");
        repository.enableTotp(player, secret);
        repository.setPin(player, "864213");
        String code = TotpCode.generate(secret, NOW);

        assertThat(verify.verify(player, code, NOW)).isEqualTo(VerifyResult.SUCCESS);
        assertThat(verify.verify(player, code, NOW)).isEqualTo(VerifyResult.INVALID);
        assertThat(verify.verify(player, "864213", NOW)).isEqualTo(VerifyResult.SUCCESS);
    }

    @Test
    void eitherFactorUnlocksWhenBothAreEnrolled() {
        TwoFactorSecret secret = new TwoFactorSecret("JBSWY3DPEHPK3PXP");
        repository.setPin(player, "4321");
        repository.enableTotp(player, secret);

        assertThat(verify.verify(player, "4321", NOW)).isEqualTo(VerifyResult.SUCCESS);
        assertThat(verify.verify(player, TotpCode.generate(secret, NOW), NOW)).isEqualTo(VerifyResult.SUCCESS);
        assertThat(verify.verify(player, "0000", NOW)).isEqualTo(VerifyResult.INVALID);
    }
}
