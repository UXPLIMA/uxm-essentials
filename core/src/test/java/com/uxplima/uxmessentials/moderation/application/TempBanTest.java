package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A tempban is always timed, capped by the issuer's tier, removes the player at once and keeps them out until the
 * moment it runs out; the login gate checks lockdown first, then the ban, then the address.
 */
class TempBanTest {

    private static final String ADDRESS = "198.51.100.9";
    private static final String CAP_NODE = "uxmessentials.moderation.ban.maxduration";

    private final MutableClock clock = new MutableClock(NOW);
    private final SanctionRecords records = new SanctionRecords();
    private final OnlinePlayers online = new OnlinePlayers().connect(SAM, ADDRESS);
    private final FakePermissions permissions = new FakePermissions();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final List<MessageKey> broadcasts = new ArrayList<>();
    private final TempBan tempBan = new TempBan(
            records,
            online,
            new ModerationGuard(permissions),
            notifier.notifier(),
            new SilentAudit(),
            new RecordingEvents(),
            new SanctionHistoryRecorder(new DiscardedHistory(), clock),
            new SanctionDurationLimit(permissions),
            (key, placeholders) -> broadcasts.add(key),
            SanctionSync.NONE,
            clock);
    private final LoginEnforcement login = new LoginEnforcement(
            records, new IpAlts(new NoIpHistory(), address -> address), notifier.notifier(), new SilentAudit(), clock);
    private final CheckBan checkBan = new CheckBan(records, notifier.notifier(), clock);

    private boolean letsIn(PlayerRef who) {
        return login.evaluate(who, ADDRESS, false, false).allowed();
    }

    @Test
    void aTempbanRemovesThePlayerAndHoldsTheLoginUntilTheExactMomentItEnds() {
        TempbanState.Active ban =
                tempBan.tempban(ALEX, SAM, "2h", Optional.of("grief"), false).orElseThrow();

        assertThat(ban.until()).isEqualTo(NOW.plus(Duration.ofHours(2)));
        assertThat(online.kicked()).containsOnlyKeys(SAM);
        assertThat(broadcasts).containsExactly(ModerationMessageKey.MOD_BROADCAST_TEMPBAN);

        clock.advance(Duration.ofHours(2).minusMillis(1));
        assertThat(letsIn(SAM)).isFalse();

        clock.advance(Duration.ofMillis(1));
        assertThat(letsIn(SAM)).isTrue();
    }

    @Test
    void anIssuerWithATierCapBansOnlyForTheCapAndIsToldSo() {
        permissions.quota(CAP_NODE, QuotaResult.limited(Duration.ofHours(1).toSeconds()));

        TempbanState.Active ban =
                tempBan.tempban(ALEX, SAM, "7d", Optional.empty(), true).orElseThrow();

        assertThat(ban.until()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(notifier.sent())
                .filteredOn(line -> line.viewer().equals(ALEX))
                .extracting(RecordingNotifier.Sent::key)
                .containsExactly(
                        ModerationMessageKey.MOD_DURATION_CAPPED.key(), ModerationMessageKey.TEMPBAN_APPLIED.key());
        assertThat(broadcasts).isEmpty();
    }

    @Test
    void aRequestWithinTheCapOrFromAnUnlimitedTierIsKept() {
        permissions.quota(CAP_NODE, QuotaResult.limited(Duration.ofDays(1).toSeconds()));
        assertThat(tempBan.tempban(ALEX, SAM, "12h", Optional.empty(), false)
                        .orElseThrow()
                        .until())
                .isEqualTo(NOW.plus(Duration.ofHours(12)));

        permissions.quota(CAP_NODE, QuotaResult.unlimited());
        assertThat(tempBan.tempban(ALEX, SAM, "30d", Optional.empty(), false)
                        .orElseThrow()
                        .until())
                .isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(notifier.keys()).doesNotContain(ModerationMessageKey.MOD_DURATION_CAPPED.key());
    }

    @Test
    void aTempbanWithoutAPositiveSpanBansNobody() {
        for (String raw : List.of("", "perm", "permanent", "soon", "0m", "5x", "1h junk")) {
            assertThat(tempBan.tempban(ALEX, SAM, raw, Optional.empty(), false).errorOrThrow())
                    .as(raw)
                    .isEqualTo(ModerationError.BAD_DURATION);
        }

        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
        assertThat(online.kicked()).isEmpty();
        assertThat(letsIn(SAM)).isTrue();
    }

    @Test
    void anExemptPlayerIsNotBanned() {
        permissions.grant(SAM, ModerationGuard.EXEMPT_NODE);

        assertThat(tempBan.tempban(ALEX, SAM, "1d", Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.TARGET_EXEMPT);

        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
        assertThat(online.kicked()).isEmpty();
    }

    @Test
    void checkBanReportsALapsedBanAsNotBanned() {
        tempBan.tempban(ALEX, SAM, "1h", Optional.empty(), false);

        checkBan.show(ALEX, SAM);
        clock.advance(Duration.ofHours(1));
        checkBan.show(ALEX, SAM);

        assertThat(notifier.keys())
                .endsWith(ModerationMessageKey.MOD_CHECK_BANNED.key(), ModerationMessageKey.MOD_CHECK_NOT_BANNED.key());
    }

    @Test
    void aLockdownRefusesEveryoneWithoutTheBypass() {
        records.setLockedDown(true);

        assertThat(login.evaluate(SAM, ADDRESS, false, true).kickReason())
                .contains(ModerationMessageKey.MOD_LOCKDOWN_KICK.key());
        assertThat(login.evaluate(SAM, ADDRESS, true, false).allowed()).isTrue();
    }

    @Test
    void aBannedPlayerSeesTheBanScreenEvenOnABannedAddressAndAnAddressBanLapses() {
        records.saveIpBan(new IpBan(
                ADDRESS,
                Optional.of(NOW.plus(Duration.ofHours(3))),
                Optional.empty(),
                Optional.empty(),
                Issuer.of(ALEX),
                NOW));
        tempBan.tempban(ALEX, SAM, "1h", Optional.empty(), false);

        assertThat(login.evaluate(SAM, ADDRESS, false, false).kickReason())
                .contains(ModerationMessageKey.TEMPBAN_KICK.key());

        clock.advance(Duration.ofHours(1));
        assertThat(login.evaluate(SAM, ADDRESS, false, false).kickReason())
                .contains(ModerationMessageKey.BANIP_KICK.key());

        clock.advance(Duration.ofHours(2));
        assertThat(letsIn(SAM)).isTrue();
        assertThat(records.seen()).hasSize(3);
    }
}
