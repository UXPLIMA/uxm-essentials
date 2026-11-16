package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.PunishmentTemplate;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A tempban is always timed, capped by the issuer's tier, removes the player at once and keeps them out until the
 * moment it runs out; the login gate checks lockdown first, then the ban, then the address.
 *
 * <p>A staff ban never cuts short the ban already running: a tempban, a capped {@code /ban} or a template that would
 * end sooner is refused and the staff member is told what runs, even when that ban lands in the moment between the
 * read and the write.
 */
class TempBanTest {

    private static final String ADDRESS = "198.51.100.9";
    private static final String CAP_NODE = EnforcementPermissions.maxDuration("ban");

    private final MutableClock clock = new MutableClock(NOW);
    private final SanctionRecords records = new SanctionRecords();
    private final OnlinePlayers online = new OnlinePlayers().connect(SAM, ADDRESS);
    private final FakePermissions permissions = new FakePermissions();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final List<MessageKey> broadcasts = new ArrayList<>();
    private final RecordingEvents events = new RecordingEvents();
    private final List<PlayerRef> syncedBans = new ArrayList<>();
    private final SanctionSync sync = new SanctionSync() {
        @Override
        public void banChanged(PlayerRef target) {
            syncedBans.add(target);
        }

        @Override
        public void muteChanged(PlayerRef target) {}

        @Override
        public void ipBanChanged(String ip) {}
    };
    private final TempBan tempBan = new TempBan(
            records,
            online,
            new ModerationGuard(permissions),
            notifier.notifier(),
            new SilentAudit(),
            events,
            new SanctionHistoryRecorder(new DiscardedHistory(), clock),
            new SanctionDurationLimit(permissions),
            (key, placeholders) -> broadcasts.add(key),
            sync,
            clock);
    private final Ban ban = new Ban(
            records,
            online,
            new ModerationGuard(permissions),
            notifier.notifier(),
            new SilentAudit(),
            events,
            new SanctionHistoryRecorder(new DiscardedHistory(), clock),
            new SanctionDurationLimit(permissions),
            (key, placeholders) -> broadcasts.add(key),
            sync,
            AddressStrictness.NORMAL,
            new NoIpHistory(),
            clock);
    private final Punish punish = new Punish(
            new ResolveTemplate(
                    Map.of("spam", new PunishmentTemplate("spam", "spamming", Optional.of(Duration.ofDays(1))))),
            ban,
            tempBan,
            notifier.notifier());
    private final LoginEnforcement login = new LoginEnforcement(
            records, new IpAlts(new NoIpHistory(), address -> address), notifier.notifier(), new SilentAudit(), clock);
    private final CheckBan checkBan = new CheckBan(records, notifier.notifier(), clock);

    private boolean letsIn(PlayerRef who) {
        return login.evaluate(who, ADDRESS, false, false).allowed();
    }

    /** The lines Alex was told, newest last. */
    private List<RecordingNotifier.Sent> toldAlex() {
        return notifier.sent().stream()
                .filter(line -> line.viewer().equals(ALEX))
                .toList();
    }

    private TempbanState.Active stored() {
        return (TempbanState.Active) records.loadTempban(SAM);
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
    void aTempbanAsLongAsAPermanentBanIsRefusedAndPointsAtBan() {
        String span = Ban.PERMANENT_SPAN.toDays() + "d";

        assertThat(tempBan.tempban(ALEX, SAM, span, Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.BAD_DURATION);

        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
        assertThat(online.kicked()).isEmpty();
        assertThat(broadcasts).isEmpty();
        assertThat(toldAlex().getLast().key()).isEqualTo(ModerationMessageKey.TEMPBAN_TOO_LONG.key());
    }

    @Test
    void aTempbanJustShortOfThePermanentSpanIsStillATempban() {
        String span = Ban.PERMANENT_SPAN.minusDays(1).toDays() + "d";

        tempBan.tempban(ALEX, SAM, span, Optional.empty(), false).orElseThrow();

        assertThat(Ban.endOf(stored())).contains(NOW.plus(Ban.PERMANENT_SPAN.minusDays(1)));
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

    @Test
    void aTempbanOnAPermanentlyBannedPlayerIsRefusedAndThePermanentBanStays() {
        ban.ban(ALEX, SAM, Optional.of("cheating"), false);
        broadcasts.clear();

        assertThat(tempBan.tempban(ALEX, SAM, "1d", Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.LONGER_BAN_RUNNING);

        assertThat(stored().until()).isEqualTo(NOW.plus(Ban.PERMANENT_SPAN));
        assertThat(stored().reason()).contains("cheating");
        assertThat(toldAlex().getLast())
                .isEqualTo(new RecordingNotifier.Sent(
                        ALEX, ModerationMessageKey.BAN_ALREADY_PERMANENT.key(), Map.of("player", SAM.name())));
        assertThat(broadcasts).isEmpty();
        clock.advance(Duration.ofDays(2));
        assertThat(letsIn(SAM)).isFalse();
    }

    @Test
    void aShorterTempbanLeavesTheLongerOneAndSaysHowLongItStillRuns() {
        tempBan.tempban(ALEX, SAM, "7d", Optional.of("grief"), false);
        clock.advance(Duration.ofDays(1));

        assertThat(tempBan.tempban(ALEX, SAM, "1d", Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.LONGER_BAN_RUNNING);

        assertThat(stored().until()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(toldAlex().getLast())
                .isEqualTo(new RecordingNotifier.Sent(
                        ALEX,
                        ModerationMessageKey.BAN_ALREADY_LONGER.key(),
                        Map.of("player", SAM.name(), "remaining", "6d")));
    }

    @Test
    void aTempbanThatEndsLaterReplacesTheRunningOne() {
        tempBan.tempban(ALEX, SAM, "1h", Optional.empty(), false);

        TempbanState.Active longer =
                tempBan.tempban(ALEX, SAM, "1d", Optional.of("again"), false).orElseThrow();

        assertThat(stored()).isEqualTo(longer);
        assertThat(longer.until()).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }

    @Test
    void aBanThatHasRunOutHoldsNothingBack() {
        tempBan.tempban(ALEX, SAM, "7d", Optional.empty(), false);
        clock.advance(Duration.ofDays(8));

        assertThat(tempBan.tempban(ALEX, SAM, "1h", Optional.empty(), false).isOk())
                .isTrue();

        assertThat(stored().until()).isEqualTo(clock.instant().plus(Duration.ofHours(1)));
    }

    @Test
    void aSecondPermanentBanStillLandsSinceItCutsNothingShort() {
        ban.ban(ALEX, SAM, Optional.of("cheating"), false);
        clock.advance(Duration.ofDays(1));

        assertThat(ban.ban(ALEX, SAM, Optional.of("cheating on an alt"), false).isOk())
                .isTrue();

        assertThat(stored().reason()).contains("cheating on an alt");
    }

    @Test
    void aCappedStaffBanDoesNotShortenALongerTempban() {
        tempBan.tempban(ALEX, SAM, "30d", Optional.empty(), false);
        permissions.quota(CAP_NODE, QuotaResult.limited(Duration.ofDays(1).toSeconds()));
        int before = toldAlex().size();

        assertThat(ban.ban(ALEX, SAM, Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.LONGER_BAN_RUNNING);

        assertThat(stored().until()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(toldAlex().subList(before, toldAlex().size()))
                .extracting(RecordingNotifier.Sent::key)
                .containsExactly(
                        ModerationMessageKey.MOD_DURATION_CAPPED.key(), ModerationMessageKey.BAN_ALREADY_LONGER.key());
    }

    @Test
    void aCappedStaffMemberAskingForLongerIsToldTheirBanWasCapped() {
        tempBan.tempban(ALEX, SAM, "7d", Optional.empty(), false);
        permissions.quota(CAP_NODE, QuotaResult.limited(Duration.ofDays(1).toSeconds()));
        int before = toldAlex().size();

        assertThat(tempBan.tempban(ALEX, SAM, "30d", Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.LONGER_BAN_RUNNING);

        assertThat(toldAlex().subList(before, toldAlex().size()))
                .containsExactly(
                        new RecordingNotifier.Sent(
                                ALEX, ModerationMessageKey.MOD_DURATION_CAPPED.key(), Map.of("cap", "1d")),
                        new RecordingNotifier.Sent(
                                ALEX,
                                ModerationMessageKey.BAN_ALREADY_LONGER.key(),
                                Map.of("player", SAM.name(), "remaining", "1w")));
    }

    @Test
    void aRefusedBanKicksPublishesSyncsAndAnnouncesNothing() {
        records.saveTempban(
                SAM, TempbanState.active(NOW.plus(Ban.PERMANENT_SPAN), Issuer.of(ALEX), Optional.empty(), NOW));

        permissions.quota(CAP_NODE, QuotaResult.limited(Duration.ofDays(1).toSeconds()));

        assertThat(tempBan.tempban(ALEX, SAM, "1d", Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.LONGER_BAN_RUNNING);
        assertThat(ban.ban(ALEX, SAM, Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.LONGER_BAN_RUNNING);

        assertThat(online.kicked()).isEmpty();
        assertThat(events.published()).isEmpty();
        assertThat(syncedBans).isEmpty();
        assertThat(broadcasts).isEmpty();
    }

    @Test
    void theTimeLeftIsRoundedUpToAWholeMinute() {
        tempBan.tempban(ALEX, SAM, "7d", Optional.empty(), false);
        clock.advance(Duration.ofSeconds(2));

        tempBan.tempban(ALEX, SAM, "1d", Optional.empty(), false);

        assertThat(toldAlex().getLast().placeholders()).containsEntry("remaining", "1w");
    }

    @Test
    void aWriteThatLosesEveryRaceGivesUpAndChangesNothing() {
        records.loseEveryReplace();

        assertThatThrownBy(() -> tempBan.tempban(ALEX, SAM, "1d", Optional.empty(), false))
                .isInstanceOf(IllegalStateException.class);

        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
        assertThat(online.kicked()).isEmpty();
        assertThat(events.published()).isEmpty();
        assertThat(syncedBans).isEmpty();
        assertThat(broadcasts).isEmpty();
    }

    @Test
    void aTimedTemplateDoesNotShortenAPermanentBan() {
        ban.ban(ALEX, SAM, Optional.of("cheating"), false);

        punish.punish(ALEX, SAM, "spam", false);

        assertThat(stored().until()).isEqualTo(NOW.plus(Ban.PERMANENT_SPAN));
        assertThat(toldAlex().getLast().key()).isEqualTo(ModerationMessageKey.BAN_ALREADY_PERMANENT.key());
    }

    @Test
    void aPermanentBanLandingBetweenTheReadAndTheWriteIsNotOverwritten() {
        records.beforeNextReplace(() -> ban.ban(ALEX, SAM, Optional.of("cheating"), true));

        assertThat(tempBan.tempban(ALEX, SAM, "1d", Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.LONGER_BAN_RUNNING);

        assertThat(stored().until()).isEqualTo(NOW.plus(Ban.PERMANENT_SPAN));
        assertThat(stored().reason()).contains("cheating");
    }

    @Test
    void aShorterBanLandingBetweenTheReadAndTheWriteIsReplacedByTheLongerOne() {
        records.beforeNextReplace(() -> tempBan.tempban(ALEX, SAM, "1h", Optional.empty(), true));

        assertThat(tempBan.tempban(ALEX, SAM, "1d", Optional.empty(), false).isOk())
                .isTrue();

        assertThat(stored().until()).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }
}
