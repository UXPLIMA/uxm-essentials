package com.uxplima.uxmessentials.moderation.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmModerationActions;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmResult;
import com.uxplima.uxmessentials.api.view.UxmSanction;
import com.uxplima.uxmessentials.api.view.UxmSanctionKind;
import com.uxplima.uxmessentials.api.view.UxmWarn;
import com.uxplima.uxmessentials.moderation.application.Ban;
import com.uxplima.uxmessentials.moderation.application.IssueWarn;
import com.uxplima.uxmessentials.moderation.application.Jail;
import com.uxplima.uxmessentials.moderation.application.Kick;
import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.moderation.application.Mute;
import com.uxplima.uxmessentials.moderation.application.SanctionDurationLimit;
import com.uxplima.uxmessentials.moderation.application.SanctionHistoryRecorder;
import com.uxplima.uxmessentials.moderation.application.TempBan;
import com.uxplima.uxmessentials.moderation.application.Unban;
import com.uxplima.uxmessentials.moderation.application.Unjail;
import com.uxplima.uxmessentials.moderation.application.Unmute;
import com.uxplima.uxmessentials.moderation.application.WarnEscalator;
import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published moderation actions: the punishment a plugin hands down is the punishment the server holds, the
 * plugin is who the record names, and a refusal the module models comes back as a failure rather than an
 * exception.
 */
class ModerationActionsTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private WritingRepository repository;
    private RecordingSanctions sanctions;
    private CountingBroadcast broadcast;
    private NodePermissions permissions;
    private ActionDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new WritingRepository();
        sanctions = new RecordingSanctions();
        broadcast = new CountingBroadcast();
        permissions = new NodePermissions();
        scheduler = new ActionDoubles.InlineScheduler();
    }

    @Test
    void banningStoresTheBanAndDisconnectsThePlayer() {
        UxmResult<UxmSanction> result = actions().ban(ALICE.uuid(), "griefing").join();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.valueOrThrow().kind()).isEqualTo(UxmSanctionKind.BAN);
        assertThat(result.valueOrThrow().reason()).contains("griefing");
        assertThat(repository.tempban(ALICE)).isInstanceOf(TempbanState.Active.class);
        assertThat(sanctions.kicked).containsExactly(ALICE.uuid());
    }

    @Test
    void aPermanentBanIsPublishedAsHavingNoExpiry() {
        UxmSanction ban = actions().ban(ALICE.uuid()).join().valueOrThrow();

        assertThat(ban.isPermanent())
                .as("stored as a far-future span, but a consumer should not have to know that")
                .isTrue();
        assertThat(ban.expiresAt()).isEmpty();
    }

    @Test
    void theBanNamesThePluginThatAskedForIt() {
        UxmSanction ban =
                actions("AntiCheat").ban(ALICE.uuid(), "flying").join().valueOrThrow();

        assertThat(ban.issuer().name()).isEqualTo("AntiCheat");
    }

    @Test
    void aTimedBanCarriesTheSpanItWasGiven() {
        UxmSanction ban = actions()
                .tempBan(ALICE.uuid(), Duration.ofDays(3), "cheating")
                .join()
                .valueOrThrow();

        assertThat(ban.expiresAt()).contains(NOW.plus(Duration.ofDays(3)));
    }

    @Test
    void aBanThatWouldAlreadyHaveLapsedIsRefusedBeforeAnythingIsWritten() {
        assertThatThrownBy(() -> actions().tempBan(ALICE.uuid(), Duration.ZERO, "nothing"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.tempban(ALICE)).isInstanceOf(TempbanState.None.class);
    }

    @Test
    void unbanningLiftsTheBanAndUnbanningACleanPlayerSaysSo() {
        actions().ban(ALICE.uuid(), "griefing").join();

        assertThat(actions().unban(ALICE.uuid()).join().succeeded()).isTrue();
        assertThat(actions().unban(ALICE.uuid()).join().failureOrThrow().is(UxmFailure.NOT_FOUND))
                .isTrue();
    }

    @Test
    void aPermanentMuteIsPermanentAndATimedOneIsTimed() {
        assertThat(actions().mute(ALICE.uuid(), "spam").join().valueOrThrow().isPermanent())
                .isTrue();

        UxmSanction timed = actions()
                .tempMute(ALICE.uuid(), Duration.ofHours(2), "spam")
                .join()
                .valueOrThrow();
        assertThat(timed.expiresAt()).contains(NOW.plus(Duration.ofHours(2)));
    }

    @Test
    void unmutingLiftsTheMuteAndUnmutingACleanPlayerSaysSo() {
        actions().mute(ALICE.uuid(), "spam").join();

        assertThat(actions().unmute(ALICE.uuid()).join().succeeded()).isTrue();
        assertThat(actions().unmute(ALICE.uuid()).join().failureOrThrow().is(UxmFailure.NOT_FOUND))
                .isTrue();
    }

    @Test
    void kickingAnOfflinePlayerSaysSoRatherThanReportingSuccess() {
        UxmOutcome outcome = actions().kick(UUID.randomUUID(), "leave").join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.PLAYER_OFFLINE)).isTrue();
        assertThat(sanctions.kicked).isEmpty();
    }

    @Test
    void warningRecordsTheWarningAndAnswersWithIt() {
        UxmWarn warn = actions("Reports").warn(ALICE.uuid(), "language").join().valueOrThrow();

        assertThat(warn.reason()).contains("language");
        assertThat(warn.issuer().name()).isEqualTo("Reports");
        assertThat(repository.warns).hasSize(1);
    }

    @Test
    void anExemptPlayerIsRefusedExactlyAsTheyAreForStaff() {
        permissions.grant(ALICE, ModerationGuard.EXEMPT_NODE);

        UxmResult<UxmSanction> result = actions().ban(ALICE.uuid(), "griefing").join();

        assertThat(result.failureOrThrow().is(UxmFailure.REFUSED)).isTrue();
        assertThat(repository.tempban(ALICE)).isInstanceOf(TempbanState.None.class);
    }

    @Test
    void jailingSendsThePlayerToTheJailAndAnUnknownJailIsNotFound() {
        UxmSanction sentence =
                actions().jail(ALICE.uuid(), "cells", "griefing").join().valueOrThrow();

        assertThat(sentence.kind()).isEqualTo(UxmSanctionKind.JAIL);
        assertThat(sanctions.jailed).containsExactly("cells");

        assertThat(actions()
                        .jail(ALICE.uuid(), "nowhere", "griefing")
                        .join()
                        .failureOrThrow()
                        .is(UxmFailure.NOT_FOUND))
                .isTrue();
    }

    @Test
    void releasingAPlayerNobodyJailedSaysSo() {
        assertThat(actions().unjail(ALICE.uuid()).join().failureOrThrow().is(UxmFailure.NOT_FOUND))
                .isTrue();
    }

    @Test
    void thePunishmentIsAnnouncedUnlessTheCallerAsksOtherwise() {
        actions().ban(ALICE.uuid(), "griefing").join();
        assertThat(broadcast.announcements).isEqualTo(1);

        actions().silently().ban(ALICE.uuid(), "griefing").join();
        assertThat(broadcast.announcements)
                .as("a plugin writing its own announcement should not have the server told twice")
                .isEqualTo(1);
    }

    @Test
    void everyWriteRunsOnTheServersOwnThread() {
        // These use cases disconnect players and announce to everybody, neither of which a worker thread may do.
        actions().ban(ALICE.uuid(), "griefing").join();
        actions().unban(ALICE.uuid()).join();

        assertThat(scheduler.asyncCalls()).isZero();
    }

    private UxmModerationActions actions() {
        return actions("TestPlugin");
    }

    private UxmModerationActions actions(String source) {
        return new ModerationActions(writes(), new QueryDoubles.MapLookup().with(ALICE), scheduler, source);
    }

    private ModerationApiWrites writes() {
        ModerationGuard guard = new ModerationGuard(permissions);
        SanctionDurationLimit limit = new SanctionDurationLimit(permissions);
        SanctionHistoryRecorder history = new SanctionHistoryRecorder(new CollectingHistory(), CLOCK);
        ModerationAudit audit = new SilentAudit();
        SanctionSync sync = new NoSync();
        ActionDoubles.RecordingEvents events = new ActionDoubles.RecordingEvents();
        Mute mute = new Mute(
                repository,
                guard,
                ActionDoubles.silentNotifier(),
                audit,
                events,
                history,
                limit,
                broadcast,
                sync,
                CLOCK);
        TempBan tempBan = new TempBan(
                repository,
                sanctions,
                guard,
                ActionDoubles.silentNotifier(),
                audit,
                events,
                history,
                limit,
                broadcast,
                sync,
                CLOCK);
        Ban ban = new Ban(
                repository,
                sanctions,
                guard,
                ActionDoubles.silentNotifier(),
                audit,
                events,
                history,
                limit,
                broadcast,
                sync,
                AddressStrictness.NORMAL,
                ActionDoubles.emptyIpHistory(),
                CLOCK);
        Kick kick = new Kick(sanctions, guard, ActionDoubles.silentNotifier(), audit, history, broadcast);
        WarnEscalator escalator =
                new WarnEscalator(WarnEscalation.NONE, mute, tempBan, ban, kick, ActionDoubles.silentNotifier());
        return new ModerationApiWrites(
                ban,
                tempBan,
                new Unban(repository, ActionDoubles.silentNotifier(), audit, history),
                mute,
                new Unmute(repository, ActionDoubles.silentNotifier(), audit, events, history, CLOCK),
                kick,
                new IssueWarn(
                        repository,
                        guard,
                        ActionDoubles.silentNotifier(),
                        audit,
                        events,
                        history,
                        broadcast,
                        escalator,
                        CLOCK),
                new Jail(
                        repository,
                        new NamedJails(),
                        sanctions,
                        guard,
                        ActionDoubles.silentNotifier(),
                        audit,
                        events,
                        CLOCK),
                new Unjail(repository, sanctions, ActionDoubles.silentNotifier(), audit, events, CLOCK));
    }

    /** A moderation store that stores, since these tests are about what the write left behind. */
    private static final class WritingRepository implements ModerationRepository {

        private final Map<UUID, MuteState> mutes = new HashMap<>();
        private final Map<UUID, JailState> jails = new HashMap<>();
        private final Map<UUID, TempbanState> tempbans = new HashMap<>();
        private final List<Warn> warns = new ArrayList<>();

        TempbanState tempban(PlayerRef who) {
            return tempbans.getOrDefault(who.uuid(), TempbanState.none());
        }

        @Override
        public ModerationProfile load(PlayerRef target) {
            return new ModerationProfile(target, loadMute(target), loadJail(target), loadTempban(target));
        }

        @Override
        public MuteState loadMute(PlayerRef target) {
            return mutes.getOrDefault(target.uuid(), MuteState.none());
        }

        @Override
        public JailState loadJail(PlayerRef target) {
            return jails.getOrDefault(target.uuid(), JailState.none());
        }

        @Override
        public TempbanState loadTempban(PlayerRef target) {
            return tempbans.getOrDefault(target.uuid(), TempbanState.none());
        }

        @Override
        public List<BanEntry> activeBans(Instant now, int limit) {
            return List.of();
        }

        @Override
        public List<MuteEntry> activeMutes(Instant now, int limit) {
            return List.of();
        }

        @Override
        public List<JailEntry> activeJails(Instant now, int limit) {
            return List.of();
        }

        @Override
        public void saveMute(PlayerRef target, MuteState state) {
            mutes.put(target.uuid(), state);
        }

        @Override
        public void saveJail(PlayerRef target, JailState state) {
            jails.put(target.uuid(), state);
        }

        @Override
        public void saveTempban(PlayerRef target, TempbanState state) {
            tempbans.put(target.uuid(), state);
        }

        @Override
        public int appendWarn(PlayerRef target, Warn warn) {
            warns.add(warn);
            return warns.size();
        }

        @Override
        public List<Warn> warns(PlayerRef target, Instant now) {
            return List.copyOf(warns);
        }

        @Override
        public int clearWarns(PlayerRef target) {
            int had = warns.size();
            warns.clear();
            return had;
        }

        @Override
        public int clearWarnsByActor(PlayerRef target, PlayerRef actor) {
            return clearWarns(target);
        }

        @Override
        public void saveIpBan(IpBan ban) {}

        @Override
        public boolean removeIpBan(String ip) {
            return false;
        }

        @Override
        public Optional<IpBan> activeIpBan(String ip, Instant now) {
            return Optional.empty();
        }

        @Override
        public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {}

        @Override
        public Optional<SeenRecord> seen(PlayerRef who) {
            return Optional.empty();
        }

        @Override
        public void ensureUserExists(PlayerRef target, Instant at) {}

        @Override
        public boolean isLockedDown() {
            return false;
        }

        @Override
        public void setLockedDown(boolean enabled) {}
    }

    /** Remembers who was disconnected and where anybody was sent, which is all the server side does here. */
    private static final class RecordingSanctions implements Sanctions {

        private final List<UUID> kicked = new ArrayList<>();
        private final List<String> jailed = new ArrayList<>();

        @Override
        public void kick(PlayerRef target, MessageKey reasonKey, String reasonText) {
            kicked.add(target.uuid());
        }

        @Override
        public Collection<PlayerRef> onlinePlayers() {
            return List.of();
        }

        @Override
        public void freeze(PlayerRef target) {}

        @Override
        public void unfreeze(PlayerRef target) {}

        @Override
        public boolean isFrozen(PlayerRef target) {
            return false;
        }

        @Override
        public void sendToJail(PlayerRef target, String jail) {
            jailed.add(jail);
        }

        @Override
        public void releaseFromJail(PlayerRef target) {}
    }

    /** One jail, so both the known and the unknown name are exercised. */
    private static final class NamedJails implements JailDirectory {

        @Override
        public boolean exists(String jail) {
            return "cells".equals(jail);
        }

        @Override
        public boolean isWallClock(String jail) {
            return true;
        }

        @Override
        public List<String> names() {
            return List.of("cells");
        }
    }

    /** Counts what the server was told, which is the one thing {@code silently} changes. */
    private static final class CountingBroadcast implements SanctionBroadcast {

        private int announcements;

        @Override
        public void announce(MessageKey key, Map<String, String> placeholders) {
            announcements++;
        }
    }

    /** Grants nothing until a test says otherwise: not exempt, and no duration cap. */
    private static final class NodePermissions implements Permissions {

        private final Set<String> granted = new HashSet<>();

        void grant(PlayerRef who, String node) {
            granted.add(who.uuid() + "|" + node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.contains(who.uuid() + "|" + node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class CollectingHistory implements SanctionHistory {

        private final List<SanctionHistoryEntry> entries = new ArrayList<>();

        @Override
        public void append(SanctionHistoryEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<SanctionHistoryEntry> banHistory(UUID target, int limit) {
            return List.copyOf(entries);
        }

        @Override
        public List<SanctionHistoryEntry> muteHistory(UUID target, int limit) {
            return List.copyOf(entries);
        }

        @Override
        public List<SanctionHistoryEntry> recentForTarget(UUID target, int limit) {
            return List.copyOf(entries);
        }

        @Override
        public List<SanctionHistoryEntry> recentByActor(UUID actor, int limit) {
            return List.copyOf(entries);
        }

        @Override
        public List<SanctionHistoryEntry> allSince(Instant threshold, int limit) {
            return List.copyOf(entries);
        }
    }

    /** The audit is not what these tests are about; it only has to not blow up. */
    private static final class SilentAudit implements ModerationAudit {

        @Override
        public void muted(
                PlayerRef actor, PlayerRef target, Optional<String> duration, boolean ok, Optional<String> reason) {}

        @Override
        public void unmuted(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void jailed(
                PlayerRef actor,
                PlayerRef target,
                String jail,
                Optional<String> duration,
                boolean ok,
                Optional<String> reason) {}

        @Override
        public void unjailed(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void tempbanned(
                PlayerRef actor, PlayerRef target, String duration, boolean ok, Optional<String> reason) {}

        @Override
        public void unbanned(PlayerRef actor, PlayerRef target, boolean ok) {}

        @Override
        public void warned(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void clearedWarns(PlayerRef actor, PlayerRef target, boolean ok, int count) {}

        @Override
        public void kicked(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void kickedAll(PlayerRef actor, int affected, Optional<String> reason) {}

        @Override
        public void froze(PlayerRef actor, PlayerRef target, boolean frozen, boolean ok) {}

        @Override
        public void ipBanned(
                PlayerRef actor,
                String targetIp,
                Optional<UUID> target,
                Optional<String> duration,
                boolean ok,
                Optional<String> reason) {}

        @Override
        public void ipUnbanned(PlayerRef actor, String targetIp, boolean ok) {}

        @Override
        public void altDetected(UUID uuid, String ip, List<UUID> matchedAlts, boolean kicked) {}

        @Override
        public void jailLocationDefined(PlayerRef actor, String jail) {}

        @Override
        public void jailLocationRemoved(PlayerRef actor, String jail) {}

        @Override
        public void lockdown(UUID actor, boolean enabled) {}
    }

    /** No peers to tell, which is what a single server looks like. */
    private static final class NoSync implements SanctionSync {

        @Override
        public void banChanged(PlayerRef target) {}

        @Override
        public void muteChanged(PlayerRef target) {}
    }
}
