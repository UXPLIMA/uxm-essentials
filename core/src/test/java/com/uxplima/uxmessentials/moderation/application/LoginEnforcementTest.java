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
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A banned address is refused at login, except for an exempt holder, as the live kick spares them. On an offline-mode
 * server a ban, a mute and a jail also follow their player into the other spellings of their name, which log in as
 * other accounts: by default into an account first seen after the sanction, or into every spelling when the server
 * says so.
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

        // A first login under the new spelling: the account did not exist when the ban was issued.
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

    @Test
    void offlineModeAMuteAndAJailFollowANewAccountUnderAnotherSpelling() {
        repository.saveMute(STEVE, STEVES_MUTE);
        repository.saveJail(STEVE, STEVES_JAIL);

        LoginEnforcement.Decision decision =
                Logins.check(offlineMode(name -> List.of(STEVE), false), UPPER_STEVE, HOME, false, false);

        assertThat(decision.allowed()).isTrue();
        assertThat(repository.loadMute(UPPER_STEVE)).isEqualTo(STEVES_MUTE);
        assertThat(repository.loadJail(UPPER_STEVE)).isEqualTo(STEVES_JAIL);
    }

    @Test
    void offlineModeAnAccountPlayingBeforeTheSanctionsIsSomebodyElseByDefault() {
        banSteve();
        repository.saveMute(STEVE, STEVES_MUTE);
        repository.saveJail(STEVE, STEVES_JAIL);
        playingSinceLastMonth(UPPER_STEVE);

        LoginEnforcement.Decision decision =
                Logins.check(offlineMode(name -> List.of(STEVE, UPPER_STEVE), false), UPPER_STEVE, HOME, false, false);

        assertThat(decision.allowed()).isTrue();
        assertThat(repository.loadMute(UPPER_STEVE)).isEqualTo(MuteState.none());
        assertThat(repository.loadJail(UPPER_STEVE)).isEqualTo(JailState.none());
    }

    @Test
    void offlineModeEverySpellingIsReachedWhenTheServerSaysSo() {
        banSteve();
        repository.saveMute(STEVE, STEVES_MUTE);
        playingSinceLastMonth(UPPER_STEVE);
        LoginEnforcement everySpelling =
                offlineMode(name -> List.of(STEVE, UPPER_STEVE), false, CaseVariantSanctions.Reach.ALL);

        LoginEnforcement.Decision decision = Logins.check(everySpelling, UPPER_STEVE, HOME, false, false);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.kickReason()).contains(ModerationMessageKey.TEMPBAN_KICK.key());
        assertThat(repository.loadMute(UPPER_STEVE)).isEqualTo(STEVES_MUTE);
    }

    @Test
    void offlineModeAMuteOfTheAccountsOwnIsKept() {
        MuteState own = MuteState.permanent(Issuer.of(ALEX), Optional.of("spam"), NOW.minus(Duration.ofDays(2)));
        repository.saveMute(UPPER_STEVE, own);
        repository.saveMute(STEVE, STEVES_MUTE);

        Logins.check(offlineMode(name -> List.of(STEVE), false), UPPER_STEVE, HOME, false, false);

        assertThat(repository.loadMute(UPPER_STEVE)).isEqualTo(own);
    }

    private static final MuteState STEVES_MUTE = MuteState.timed(
            NOW.plus(Duration.ofDays(1)), Issuer.of(ALEX), Optional.of("slurs"), NOW.minus(Duration.ofHours(1)));
    private static final JailState STEVES_JAIL =
            JailState.permanent("cell", Issuer.of(ALEX), Optional.of("grief"), NOW.minus(Duration.ofHours(1)));

    private void banSteve() {
        repository.saveTempban(
                STEVE,
                TempbanState.active(NOW.plus(Duration.ofDays(1)), Issuer.of(ALEX), Optional.of("cheating"), NOW));
    }

    private void playingSinceLastMonth(PlayerRef account) {
        repository.recordSeen(SeenRecord.firstJoin(account, Optional.of(HOME), NOW.minus(Duration.ofDays(30))));
    }

    private LoginEnforcement offlineMode(NameHolders holders, boolean refuseCaseVariants) {
        return offlineMode(holders, refuseCaseVariants, CaseVariantSanctions.Reach.NEWER);
    }

    private LoginEnforcement offlineMode(
            NameHolders holders, boolean refuseCaseVariants, CaseVariantSanctions.Reach reach) {
        return new LoginEnforcement(
                repository,
                new IpAlts(new NoIpHistory(), address -> address),
                notifier.notifier(),
                new SilentAudit(),
                CLOCK,
                new CaseVariantSanctions(repository, holders, reach),
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
