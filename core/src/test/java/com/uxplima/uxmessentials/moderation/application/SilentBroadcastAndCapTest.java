package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationAction;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationStep;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctionHistory;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctions;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes.RecordingBroadcast;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes.RecordingSink;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MOD-A behaviours through the real sanction use cases: the {@code silent} flag suppresses only the staff
 * broadcast (the actor and target notices still go out); the {@code maxduration} tier reduces an over-cap
 * request and tells the actor; and the warn-escalation ladder applies the matching sanction once, without
 * recursing, and respecting silence.
 */
class SilentBroadcastAndCapTest {

    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "staff");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");

    private FakeModerationRepository repository;
    private RecordingModerationAudit audit;
    private ModerationFakes.RecordingEvents events;
    private RecordingBroadcast broadcast;
    private RecordingSink sink;
    private ModerationNotifier notifier;
    private ModerationGuard guard;
    private SanctionHistoryRecorder history;
    private Clock clock;

    @BeforeEach
    void setUp() {
        repository = new FakeModerationRepository();
        audit = new RecordingModerationAudit();
        events = new ModerationFakes.RecordingEvents();
        broadcast = ModerationFakes.broadcast();
        sink = new RecordingSink();
        notifier = ModerationFakes.recordingNotifier(sink);
        guard = new ModerationGuard(ModerationFakes.exempt());
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        history = new SanctionHistoryRecorder(new FakeSanctionHistory(), clock);
    }

    // --- silent vs non-silent ---------------------------------------------------------------------------

    @Test
    void nonSilentBanBroadcastsAndNotifiesTheActor() {
        ban(ModerationFakes.exempt()).ban(ACTOR, TARGET, Optional.of("griefing"), false);

        assertThat(broadcast.announced).singleElement().satisfies(row -> {
            assertThat(row.key()).isEqualTo(ModerationMessageKey.MOD_BROADCAST_BAN);
            assertThat(row.placeholders()).containsEntry("actor", "staff").containsEntry("target", "griefer");
        });
        assertThat(sink.sent(ACTOR, ModerationMessageKey.BAN_APPLIED)).isTrue();
    }

    @Test
    void silentBanSuppressesOnlyTheBroadcast() {
        FakeSanctions sanctions = new FakeSanctions(TARGET);
        new Ban(
                        repository,
                        sanctions,
                        guard,
                        notifier,
                        audit,
                        events,
                        history,
                        new SanctionDurationLimit(ModerationFakes.exempt()),
                        broadcast,
                        AddressStrictness.NORMAL,
                        clock)
                .ban(ACTOR, TARGET, Optional.of("griefing"), true);

        assertThat(broadcast.announced).isEmpty();
        // The actor confirmation and the target's removal (the kick screen) still happen.
        assertThat(sink.sent(ACTOR, ModerationMessageKey.BAN_APPLIED)).isTrue();
        assertThat(sanctions.kicked).containsExactly(TARGET);
        assertThat(repository.loadTempban(TARGET)).isInstanceOf(TempbanState.Active.class);
    }

    @Test
    void nonSilentMuteBroadcastsAndTellsTheTarget() {
        mute(ModerationFakes.exempt()).mute(ACTOR, TARGET, "1h", Optional.of("spam"), false);

        assertThat(broadcast.announced).singleElement().satisfies(row -> assertThat(row.key())
                .isEqualTo(ModerationMessageKey.MOD_BROADCAST_MUTE));
        assertThat(sink.sent(TARGET, ModerationMessageKey.MUTE_NOTIFY_TARGET)).isTrue();
    }

    @Test
    void silentMuteStillTellsTheTargetButDoesNotBroadcast() {
        mute(ModerationFakes.exempt()).mute(ACTOR, TARGET, "1h", Optional.of("spam"), true);

        assertThat(broadcast.announced).isEmpty();
        assertThat(sink.sent(TARGET, ModerationMessageKey.MUTE_NOTIFY_TARGET)).isTrue();
        assertThat(repository.loadMute(TARGET)).isInstanceOf(MuteState.Timed.class);
    }

    @Test
    void nonSilentWarnBroadcastsAndSilentDoesNot() {
        IssueWarn warn = issueWarn(WarnEscalation.NONE);

        warn.warn(ACTOR, TARGET, Optional.of("first"), false);
        assertThat(broadcast.announced).singleElement().satisfies(row -> assertThat(row.key())
                .isEqualTo(ModerationMessageKey.MOD_BROADCAST_WARN));
        assertThat(sink.sent(TARGET, ModerationMessageKey.WARN_NOTIFY_TARGET)).isTrue();

        broadcast.announced.clear();
        warn.warn(ACTOR, TARGET, Optional.of("second"), true);
        assertThat(broadcast.announced).isEmpty();
        assertThat(sink.sent(ACTOR, ModerationMessageKey.WARN_APPLIED)).isTrue();
    }

    // --- duration tier cap ------------------------------------------------------------------------------

    @Test
    void anOverCapTempbanIsReducedAndTheActorIsTold() {
        FakeSanctions sanctions = new FakeSanctions(TARGET);
        new TempBan(
                        repository,
                        sanctions,
                        guard,
                        notifier,
                        audit,
                        events,
                        history,
                        new SanctionDurationLimit(ModerationFakes.capping(3600)),
                        broadcast,
                        clock)
                .tempban(ACTOR, TARGET, "1d", Optional.of("cheating"), false);

        TempbanState stored = repository.loadTempban(TARGET);
        assertThat(stored.isActiveAt(NOW.plus(Duration.ofMinutes(59)))).isTrue();
        assertThat(stored.isActiveAt(NOW.plus(Duration.ofMinutes(61)))).isFalse();
        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_DURATION_CAPPED)).isTrue();
        assertThat(broadcast.announced).singleElement().satisfies(row -> assertThat(row.placeholders())
                .containsEntry("duration", "1h"));
    }

    @Test
    void anUnlimitedActorIsNotCapped() {
        FakeSanctions sanctions = new FakeSanctions(TARGET);
        new TempBan(
                        repository,
                        sanctions,
                        guard,
                        notifier,
                        audit,
                        events,
                        history,
                        new SanctionDurationLimit(ModerationFakes.exempt()),
                        broadcast,
                        clock)
                .tempban(ACTOR, TARGET, "1d", Optional.of("cheating"), false);

        assertThat(repository.loadTempban(TARGET).isActiveAt(NOW.plus(Duration.ofHours(23))))
                .isTrue();
        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_DURATION_CAPPED)).isFalse();
    }

    @Test
    void aPermanentBanByACappedActorIsReducedToTheCap() {
        FakeSanctions sanctions = new FakeSanctions(TARGET);
        new Ban(
                        repository,
                        sanctions,
                        guard,
                        notifier,
                        audit,
                        events,
                        history,
                        new SanctionDurationLimit(ModerationFakes.capping(3600)),
                        broadcast,
                        AddressStrictness.NORMAL,
                        clock)
                .ban(ACTOR, TARGET, Optional.of("griefing"), false);

        TempbanState stored = repository.loadTempban(TARGET);
        assertThat(stored.isActiveAt(NOW.plus(Duration.ofMinutes(59)))).isTrue();
        assertThat(stored.isActiveAt(NOW.plus(Duration.ofDays(100)))).isFalse();
        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_DURATION_CAPPED)).isTrue();
        // A reduced "permanent" ban announces as a tempban, not the permanent line.
        assertThat(broadcast.announced).singleElement().satisfies(row -> assertThat(row.key())
                .isEqualTo(ModerationMessageKey.MOD_BROADCAST_TEMPBAN));
    }

    // --- warn escalation --------------------------------------------------------------------------------

    @Test
    void theConfiguredRungAppliesAMuteOnTheMatchingWarnAndTellsTheActor() {
        WarnEscalation ladder = new WarnEscalation(
                List.of(new EscalationStep(3, EscalationAction.TEMPMUTE, Optional.of(Duration.ofHours(1)))));
        IssueWarn warn = issueWarn(ladder);

        warn.warn(ACTOR, TARGET, Optional.empty(), false);
        warn.warn(ACTOR, TARGET, Optional.empty(), false);
        assertThat(repository.loadMute(TARGET)).isInstanceOf(MuteState.None.class);

        warn.warn(ACTOR, TARGET, Optional.empty(), false);

        // The 3rd warning trips the rung: a one-hour tempmute is now in force.
        MuteState mute = repository.loadMute(TARGET);
        assertThat(mute).isInstanceOf(MuteState.Timed.class);
        assertThat(mute.isActiveAt(NOW.plus(Duration.ofMinutes(30)))).isTrue();
        assertThat(mute.isActiveAt(NOW.plus(Duration.ofHours(2)))).isFalse();
        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_WARN_ESCALATED)).isTrue();
        // The escalated mute does not itself re-enter escalation: only the three warns appended.
        assertThat(repository.warns(TARGET, NOW)).hasSize(3);
    }

    @Test
    void aCountWithNoRungEscalatesNothing() {
        WarnEscalation ladder =
                new WarnEscalation(List.of(new EscalationStep(5, EscalationAction.KICK, Optional.empty())));
        IssueWarn warn = issueWarn(ladder);

        warn.warn(ACTOR, TARGET, Optional.empty(), false);

        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_WARN_ESCALATED)).isFalse();
        assertThat(repository.loadMute(TARGET)).isInstanceOf(MuteState.None.class);
    }

    @Test
    void aSilentWarnEscalatesSilently() {
        WarnEscalation ladder = new WarnEscalation(
                List.of(new EscalationStep(1, EscalationAction.TEMPMUTE, Optional.of(Duration.ofHours(1)))));
        IssueWarn warn = issueWarn(ladder);

        warn.warn(ACTOR, TARGET, Optional.empty(), true);

        // The escalated mute is applied, but nothing was broadcast (neither the warn nor the mute).
        assertThat(repository.loadMute(TARGET)).isInstanceOf(MuteState.Timed.class);
        assertThat(broadcast.announced).isEmpty();
        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_WARN_ESCALATED)).isTrue();
    }

    // --- builders ---------------------------------------------------------------------------------------

    private Ban ban(Permissions perms) {
        return new Ban(
                repository,
                new FakeSanctions(TARGET),
                guard,
                notifier,
                audit,
                events,
                history,
                new SanctionDurationLimit(perms),
                broadcast,
                AddressStrictness.NORMAL,
                clock);
    }

    private Mute mute(Permissions perms) {
        return new Mute(
                repository,
                guard,
                notifier,
                audit,
                events,
                history,
                new SanctionDurationLimit(perms),
                broadcast,
                clock);
    }

    private IssueWarn issueWarn(WarnEscalation ladder) {
        SanctionDurationLimit limit = new SanctionDurationLimit(ModerationFakes.exempt());
        Mute mute = new Mute(repository, guard, notifier, audit, events, history, limit, broadcast, clock);
        TempBan tempBan = new TempBan(
                repository,
                new FakeSanctions(TARGET),
                guard,
                notifier,
                audit,
                events,
                history,
                limit,
                broadcast,
                clock);
        Ban ban = new Ban(
                repository,
                new FakeSanctions(TARGET),
                guard,
                notifier,
                audit,
                events,
                history,
                limit,
                broadcast,
                AddressStrictness.NORMAL,
                clock);
        Kick kick = new Kick(new FakeSanctions(TARGET), guard, notifier, audit, history, broadcast);
        WarnEscalator escalator = new WarnEscalator(ladder, mute, tempBan, ban, kick, notifier);
        return new IssueWarn(repository, guard, notifier, audit, events, history, broadcast, escalator, clock);
    }
}
