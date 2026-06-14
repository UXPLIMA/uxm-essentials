package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctionHistory;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes.FixedPlayers;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes.RecordingSink;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MOD-C4: {@code /staffrollback} revokes a staff member's still-ACTIVE sanctions. The crux is that the
 * append-only history carries no active flag, so every revoke is gated by a live-state read at the injected
 * clock: a ban already lifted (or expired, or re-banned by someone else then lifted) is not touched, only what
 * is currently in effect is revoked. Targets are deduped per action so a target sanctioned twice is revoked once.
 */
class StaffRollbackTest {

    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "operator");
    private static final UUID STAFF_UUID = UUID.randomUUID();
    private static final PlayerRef STAFF = new PlayerRef(STAFF_UUID, "rogue");

    private FakeModerationRepository repository;
    private FakeSanctionHistory history;
    private RecordingModerationAudit audit;
    private DomainEventPublisher events;
    private SanctionHistoryRecorder recorder;
    private ModerationNotifier notifier;
    private RecordingSink sink;
    private Clock clock;

    private final java.util.Map<UUID, PlayerRef> directory = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        repository = new FakeModerationRepository();
        history = new FakeSanctionHistory();
        audit = new RecordingModerationAudit();
        events = new ModerationFakes.RecordingEvents();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        recorder = new SanctionHistoryRecorder(history, clock);
        sink = new RecordingSink();
        notifier = ModerationFakes.recordingNotifier(sink);
        directory.put(STAFF_UUID, STAFF);
    }

    private StaffRollback rollback() {
        PlayerLookup players = new FixedPlayers(Map.copyOf(directory), java.util.Set.of());
        Unban unban = new Unban(repository, notifier, audit, recorder);
        Unmute unmute = new Unmute(repository, notifier, audit, events, recorder, clock);
        ClearWarns clearWarns = new ClearWarns(repository, notifier, audit);
        return new StaffRollback(history, repository, players, unban, unmute, clearWarns, notifier, clock);
    }

    private PlayerRef target(String name) {
        PlayerRef ref = new PlayerRef(UUID.randomUUID(), name);
        directory.put(ref.uuid(), ref);
        return ref;
    }

    private void recordStaffBan(PlayerRef victim) {
        history.append(new SanctionHistoryEntry(
                SanctionAction.BAN,
                victim.uuid(),
                Issuer.of(STAFF),
                Optional.empty(),
                NOW,
                Optional.empty(),
                Optional.empty()));
    }

    private void recordStaffMute(PlayerRef victim) {
        history.append(new SanctionHistoryEntry(
                SanctionAction.MUTE,
                victim.uuid(),
                Issuer.of(STAFF),
                Optional.empty(),
                NOW,
                Optional.empty(),
                Optional.empty()));
    }

    private void recordStaffWarn(PlayerRef victim) {
        history.append(new SanctionHistoryEntry(
                SanctionAction.WARN,
                victim.uuid(),
                Issuer.of(STAFF),
                Optional.empty(),
                NOW,
                Optional.empty(),
                Optional.empty()));
    }

    private void activeBan(PlayerRef victim) {
        repository.saveTempban(
                victim, TempbanState.active(NOW.plus(Duration.ofDays(7)), Issuer.of(STAFF), Optional.empty(), NOW));
    }

    private void activeMute(PlayerRef victim) {
        repository.saveMute(victim, MuteState.permanent(Issuer.of(STAFF), Optional.empty(), NOW));
    }

    private void activeWarn(PlayerRef victim) {
        repository.appendWarn(victim, Warn.standing(Issuer.of(STAFF), Optional.empty(), NOW));
    }

    @Test
    void liftsAStillActiveBanTheStaffApplied() {
        PlayerRef victim = target("griefer");
        recordStaffBan(victim);
        activeBan(victim);

        StaffRollback.RollbackSummary summary = rollback().rollback(ACTOR, STAFF, 100);

        assertThat(repository.loadTempban(victim)).isInstanceOf(TempbanState.None.class);
        assertThat(summary.bans()).isEqualTo(1);
        assertThat(summary.total()).isEqualTo(1);
    }

    @Test
    void liftsAStillActiveMuteTheStaffApplied() {
        PlayerRef victim = target("spammer");
        recordStaffMute(victim);
        activeMute(victim);

        StaffRollback.RollbackSummary summary = rollback().rollback(ACTOR, STAFF, 100);

        assertThat(repository.loadMute(victim)).isInstanceOf(MuteState.None.class);
        assertThat(summary.mutes()).isEqualTo(1);
    }

    @Test
    void clearsAStillActiveWarnSetTheStaffApplied() {
        PlayerRef victim = target("rulebreaker");
        recordStaffWarn(victim);
        activeWarn(victim);

        StaffRollback.RollbackSummary summary = rollback().rollback(ACTOR, STAFF, 100);

        assertThat(repository.warns(victim, NOW)).isEmpty();
        assertThat(summary.warns()).isEqualTo(1);
    }

    @Test
    void skipsABanAlreadyLiftedByTheTimeOfRollback() {
        PlayerRef victim = target("griefer");
        recordStaffBan(victim);
        // no active ban in the repository — already lifted by someone else.

        StaffRollback.RollbackSummary summary = rollback().rollback(ACTOR, STAFF, 100);

        assertThat(summary.bans()).isZero();
        assertThat(summary.total()).isZero();
    }

    @Test
    void skipsABanWhoseTimedSentenceHasLapsed() {
        PlayerRef victim = target("griefer");
        recordStaffBan(victim);
        repository.saveTempban(
                victim,
                TempbanState.active(
                        NOW.minus(Duration.ofMinutes(1)),
                        Issuer.of(STAFF),
                        Optional.empty(),
                        NOW.minus(Duration.ofHours(1))));

        StaffRollback.RollbackSummary summary = rollback().rollback(ACTOR, STAFF, 100);

        assertThat(summary.bans()).isZero();
        // The lapsed row is left untouched (it is not in effect, so the rollback does not record an unban).
        assertThat(repository.loadTempban(victim)).isInstanceOf(TempbanState.Active.class);
    }

    @Test
    void skipsUnbanUnmuteAndKickEntries() {
        PlayerRef victim = target("griefer");
        history.append(new SanctionHistoryEntry(
                SanctionAction.UNBAN,
                victim.uuid(),
                Issuer.of(STAFF),
                Optional.empty(),
                NOW,
                Optional.empty(),
                Optional.empty()));
        history.append(new SanctionHistoryEntry(
                SanctionAction.UNMUTE,
                victim.uuid(),
                Issuer.of(STAFF),
                Optional.empty(),
                NOW,
                Optional.empty(),
                Optional.empty()));
        history.append(new SanctionHistoryEntry(
                SanctionAction.KICK,
                victim.uuid(),
                Issuer.of(STAFF),
                Optional.empty(),
                NOW,
                Optional.empty(),
                Optional.empty()));
        // Even though the victim happens to be banned, no BAN row attributes to this staff, so nothing is lifted.
        activeBan(victim);

        StaffRollback.RollbackSummary summary = rollback().rollback(ACTOR, STAFF, 100);

        assertThat(summary.total()).isZero();
        assertThat(repository.loadTempban(victim)).isInstanceOf(TempbanState.Active.class);
    }

    @Test
    void dedupsATargetBannedTwiceAndLiftsOnce() {
        PlayerRef victim = target("griefer");
        recordStaffBan(victim);
        recordStaffBan(victim);
        activeBan(victim);

        StaffRollback.RollbackSummary summary = rollback().rollback(ACTOR, STAFF, 100);

        assertThat(summary.bans()).isEqualTo(1);
        assertThat(repository.loadTempban(victim)).isInstanceOf(TempbanState.None.class);
    }

    @Test
    void countsBansMutesAndWarnsTogetherInTheTotal() {
        PlayerRef banned = target("a");
        PlayerRef muted = target("b");
        PlayerRef warned = target("c");
        recordStaffBan(banned);
        activeBan(banned);
        recordStaffMute(muted);
        activeMute(muted);
        recordStaffWarn(warned);
        activeWarn(warned);

        StaffRollback.RollbackSummary summary = rollback().rollback(ACTOR, STAFF, 100);

        assertThat(summary.bans()).isEqualTo(1);
        assertThat(summary.mutes()).isEqualTo(1);
        assertThat(summary.warns()).isEqualTo(1);
        assertThat(summary.total()).isEqualTo(3);
        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_STAFFROLLBACK_SUMMARY))
                .isTrue();
    }

    @Test
    void respectsTheLimitByOnlyReadingThatManyRows() {
        PlayerRef first = target("first");
        PlayerRef second = target("second");
        recordStaffBan(first);
        activeBan(first);
        recordStaffBan(second);
        activeBan(second);

        // recentByActor returns newest-first; a limit of 1 only reaches the most recent row (second).
        StaffRollback.RollbackSummary summary = rollback().rollback(ACTOR, STAFF, 1);

        assertThat(summary.bans()).isEqualTo(1);
        assertThat(repository.loadTempban(second)).isInstanceOf(TempbanState.None.class);
        assertThat(repository.loadTempban(first)).isInstanceOf(TempbanState.Active.class);
    }

    @Test
    void reportsNoneWhenNothingActiveCanBeRevoked() {
        PlayerRef victim = target("griefer");
        recordStaffBan(victim);
        // no active sanction to revoke.

        StaffRollback.RollbackSummary summary = rollback().rollback(ACTOR, STAFF, 100);

        assertThat(summary.total()).isZero();
        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_STAFFROLLBACK_NONE))
                .isTrue();
    }

    @Test
    void reportsNoneWhenTheStaffIssuedNothing() {
        StaffRollback.RollbackSummary summary = rollback().rollback(ACTOR, STAFF, 100);

        assertThat(summary.total()).isZero();
        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_STAFFROLLBACK_NONE))
                .isTrue();
    }
}
