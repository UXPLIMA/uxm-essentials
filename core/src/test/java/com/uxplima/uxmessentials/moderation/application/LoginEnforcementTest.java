package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.NameHolders;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A banned address is refused at login, except for an exempt holder, as the live kick spares them. On an offline-mode
 * server a ban also follows its player into the other spellings of their name, which log in as other accounts.
 */
class LoginEnforcementTest {

    private static final String BANNED = "203.0.113.7";
    private static final String HOME = "198.51.100.4";
    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef UPPER_STEVE = new PlayerRef(UUID.randomUUID(), "STEVE");

    private final InMemoryIpBans repository = new InMemoryIpBans();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final LoginEnforcement login = new LoginEnforcement(
            repository,
            new IpAlts(new NoIpHistory(), address -> address),
            notifier.notifier(),
            new SilentAudit(),
            CLOCK);

    @Test
    void aPlayerOnABannedAddressIsRefusedWithTheBanScreen() {
        repository.saveIpBan(
                new IpBan(BANNED, Optional.empty(), Optional.empty(), Optional.empty(), Issuer.of(ALEX), NOW));

        LoginEnforcement.Decision decision = Logins.check(login, ALEX, BANNED, false, false);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.kickReason()).contains(ModerationMessageKey.BANIP_KICK.key());
    }

    @Test
    void offlineModeABannedPlayerIsRefusedUnderAnotherSpellingOfTheirName() {
        banSteve();
        LoginEnforcement offline = offlineMode(name -> List.of(STEVE), false);

        LoginEnforcement.Decision decision = Logins.check(offline, UPPER_STEVE, HOME, false, true);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retry()).isFalse();
        assertThat(decision.kickReason()).contains(ModerationMessageKey.TEMPBAN_KICK.key());
    }

    @Test
    void withAuthenticatedLoginsACaseVariantOfABannedNameIsSomebodyElse() {
        banSteve();

        assertThat(Logins.check(login, UPPER_STEVE, HOME, false, false).allowed())
                .isTrue();
    }

    @Test
    void anExpiredBanOnAnotherSpellingRefusesNobody() {
        repository.saveTempban(
                STEVE, TempbanState.active(NOW.minus(Duration.ofHours(1)), Issuer.of(ALEX), Optional.empty(), NOW));
        LoginEnforcement offline = offlineMode(name -> List.of(STEVE), false);

        assertThat(Logins.check(offline, UPPER_STEVE, HOME, false, false).allowed())
                .isTrue();
    }

    @Test
    void offlineModeANewLookAlikeIsRefusedOnlyWhenTheServerRefusesThem() {
        NameHolders holders = name -> List.of(STEVE);

        assertThat(Logins.check(offlineMode(holders, false), UPPER_STEVE, HOME, false, false)
                        .allowed())
                .isTrue();
        LoginEnforcement.Decision refused = Logins.check(offlineMode(holders, true), UPPER_STEVE, HOME, false, false);
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.kickReason()).contains(ModerationMessageKey.MOD_LOGIN_NAME_VARIANT.key());
    }

    @Test
    void offlineModeAnAccountThatAlreadyJoinedKeepsJoiningUnderItsSpelling() {
        LoginEnforcement strict = offlineMode(name -> List.of(STEVE, UPPER_STEVE), true);

        assertThat(Logins.check(strict, UPPER_STEVE, HOME, false, false).allowed())
                .isTrue();
        assertThat(Logins.check(strict, STEVE, HOME, false, false).allowed()).isTrue();
    }

    private void banSteve() {
        repository.saveTempban(
                STEVE,
                TempbanState.active(NOW.plus(Duration.ofDays(1)), Issuer.of(ALEX), Optional.of("cheating"), NOW));
    }

    private LoginEnforcement offlineMode(NameHolders holders, boolean refuseCaseVariants) {
        return new LoginEnforcement(
                repository,
                new IpAlts(new NoIpHistory(), address -> address),
                notifier.notifier(),
                new SilentAudit(),
                CLOCK,
                holders,
                refuseCaseVariants);
    }

    @Test
    void anExemptHolderOnABannedAddressIsLetIn() {
        repository.saveIpBan(
                new IpBan(BANNED, Optional.empty(), Optional.empty(), Optional.empty(), Issuer.of(ALEX), NOW));

        LoginEnforcement.Decision decision = Logins.check(login, ALEX, BANNED, false, true);

        assertThat(decision.allowed()).isTrue();
    }
}
