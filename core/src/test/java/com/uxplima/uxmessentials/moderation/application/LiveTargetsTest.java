package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.application.port.SeenVisibility;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.networkpresence.NetworkRoster;
import com.uxplima.uxmessentials.shared.application.networkpresence.RosterStamp;
import com.uxplima.uxmessentials.shared.application.networkpresence.TrackNetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresenceBroadcast;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A kick, a freeze or a /seen on a player connected to another backend says where they are instead of acting on
 * nothing, and writes no history, audit or broadcast. An unfreeze still runs, so the freeze record held here can
 * always be cleared.
 */
class LiveTargetsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);
    private static final PlayerRef STAFF = new PlayerRef(UUID.randomUUID(), "Staff");
    private static final PlayerRef REMOTE = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef LOCAL = new PlayerRef(UUID.randomUUID(), "Alex");

    private static final PlayerNameIndex NO_NAMES = new PlayerNameIndex() {
        @Override
        public Optional<PlayerRef> byName(String name) {
            return Optional.empty();
        }

        @Override
        public void record(UUID uuid, String name) {}

        @Override
        public void learn(UUID uuid, String name) {}
    };

    private final RecordingNotifier notifier = new RecordingNotifier();
    private final NetworkRoster presence = new NetworkRoster("survival-1", CLOCK, Duration.ofSeconds(90));
    private final FakeSanctions sanctions = new FakeSanctions();
    private final KeptHistory history = new KeptHistory();
    private final List<MessageKey> broadcasts = new ArrayList<>();
    private final RecordingAudit audit = new RecordingAudit();
    private final Kick kick = new Kick(
            sanctions,
            new ModerationGuard(new FakePermissions()),
            notifier.notifier(),
            audit,
            new SanctionHistoryRecorder(history, CLOCK),
            (key, placeholders) -> broadcasts.add(key),
            presence);
    private final Freeze freeze =
            new Freeze(sanctions, new ModerationGuard(new FakePermissions()), notifier.notifier(), audit, presence);

    LiveTargetsTest() {
        TrackNetworkPresence track = new TrackNetworkPresence(presence, NetworkPresenceBroadcast.NONE, NO_NAMES, CLOCK);
        track.remoteJoined("lobby-1", new RosterStamp(1L, 1L), REMOTE);
        track.joined(LOCAL);
    }

    @Test
    void aKickOnAPlayerOnAnotherBackendNamesTheBackendAndWritesNothing() {
        assertThat(kick.kick(STAFF, REMOTE, Optional.of("spam"), false).errorOrThrow())
                .isEqualTo(ModerationError.TARGET_ELSEWHERE);

        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(
                        STAFF, "moderation.target-elsewhere", Map.of("player", "Steve", "server", "lobby-1")));
        assertThat(sanctions.kicked).isEmpty();
        assertThat(history.entries).isEmpty();
        assertThat(broadcasts).isEmpty();
        assertThat(audit.lines).isEmpty();
    }

    @Test
    void aKickOnAPlayerHereStillKicks() {
        assertThat(kick.kick(STAFF, LOCAL, Optional.empty(), true).isErr()).isFalse();

        assertThat(sanctions.kicked).containsExactly(LOCAL);
        assertThat(history.entries).extracting(SanctionHistoryEntry::target).containsExactly(LOCAL.uuid());
    }

    @Test
    void aFreezeOnAPlayerOnAnotherBackendIsRefused() {
        assertThat(freeze.freeze(STAFF, REMOTE).errorOrThrow()).isEqualTo(ModerationError.TARGET_ELSEWHERE);

        assertThat(notifier.keys()).containsExactly("moderation.target-elsewhere");
        assertThat(sanctions.frozen).isEmpty();
        assertThat(audit.lines).isEmpty();
    }

    @Test
    void anUnfreezeOnAPlayerOnAnotherBackendClearsTheFreezeHeldHere() {
        sanctions.freeze(REMOTE);

        assertThat(freeze.unfreeze(STAFF, REMOTE).isErr()).isFalse();

        assertThat(sanctions.frozen).isEmpty();
        assertThat(audit.lines).containsExactly("unfroze Steve");
    }

    @Test
    void seenReportsAPlayerOnAnotherBackendAsOnlineThere() {
        Seen seen = new Seen(
                new InMemoryIpBans(),
                new IpAlts(new NoIpHistory(), address -> address),
                new NobodyOnline(),
                notifier.notifier(),
                false,
                CLOCK,
                presence,
                SeenVisibility.ALWAYS_VISIBLE);

        seen.seen(STAFF, REMOTE);

        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(
                        STAFF, "moderation.seen.online-elsewhere", Map.of("player", "Steve", "server", "lobby-1")));
    }

    @Test
    void seenReportsAPlayerHereAsOnlineOnThisServer() {
        InMemoryIpBans repository = new InMemoryIpBans();
        repository.recordSeen(SeenRecord.firstJoin(LOCAL, Optional.empty(), Instant.parse("2026-01-02T03:04:05Z")));
        Seen seen = new Seen(
                repository,
                new IpAlts(new NoIpHistory(), address -> address),
                new OnlyOnline(LOCAL),
                notifier.notifier(),
                false,
                CLOCK,
                presence,
                SeenVisibility.ALWAYS_VISIBLE);

        seen.seen(STAFF, LOCAL);

        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(
                        STAFF,
                        "moderation.seen.online-here",
                        Map.of("player", "Alex", "first", "2026-01-02T03:04:05Z", "server", "survival-1")));
    }

    @Test
    void seenShowsAVanishedPlayerOnAnotherBackendAsLastSeenToSomeoneWhoMayNotSeeThem() {
        Seen seen = seenHiding(REMOTE, new OnlyOnline(LOCAL));

        seen.seen(STAFF, REMOTE);

        assertThat(notifier.keys()).containsExactly("moderation.seen.report");
    }

    @Test
    void seenShowsAVanishedPlayerHereAsLastSeenToSomeoneWhoMayNotSeeThem() {
        Seen seen = seenHiding(LOCAL, new OnlyOnline(LOCAL));

        seen.seen(STAFF, LOCAL);

        assertThat(notifier.keys()).containsExactly("moderation.seen.report");
    }

    /** A /seen whose records hold both players, joined an hour ago, and whose vanish hides {@code hidden}. */
    private Seen seenHiding(PlayerRef hidden, PlayerLookup players) {
        InMemoryIpBans repository = new InMemoryIpBans();
        Instant joined = CLOCK.instant().minus(Duration.ofHours(1));
        repository.recordSeen(SeenRecord.firstJoin(LOCAL, Optional.empty(), joined));
        repository.recordSeen(SeenRecord.firstJoin(REMOTE, Optional.empty(), joined));
        return new Seen(
                repository,
                new IpAlts(new NoIpHistory(), address -> address),
                players,
                notifier.notifier(),
                false,
                CLOCK,
                presence,
                new Hiding(hidden));
    }

    /** Vanish that hides one player from a viewer without any see level, here or elsewhere. */
    private record Hiding(PlayerRef hidden) implements SeenVisibility {

        @Override
        public int seeLevel(PlayerRef viewer) {
            return 0;
        }

        @Override
        public boolean isHiddenHere(PlayerRef viewer, int viewerSeeLevel, PlayerRef target) {
            return target.equals(hidden);
        }

        @Override
        public boolean isHiddenElsewhere(int viewerSeeLevel, PlayerRef target) {
            return target.equals(hidden);
        }
    }

    /** The live side of this backend: who was kicked and who is frozen. */
    private static final class FakeSanctions implements Sanctions {

        final List<PlayerRef> kicked = new ArrayList<>();
        final Set<PlayerRef> frozen = new HashSet<>();

        @Override
        public void kick(PlayerRef target, MessageKey reasonKey, String reasonText) {
            kicked.add(target);
        }

        @Override
        public void kickAddress(String ip, Predicate<PlayerRef> kickable, Function<PlayerRef, String> reasonFor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<PlayerRef> onlinePlayers() {
            return List.of();
        }

        @Override
        public void freeze(PlayerRef target) {
            frozen.add(target);
        }

        @Override
        public void unfreeze(PlayerRef target) {
            frozen.remove(target);
        }

        @Override
        public boolean isFrozen(PlayerRef target) {
            return frozen.contains(target);
        }

        @Override
        public void sendToJail(PlayerRef target, String jail) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseFromJail(PlayerRef target) {
            throw new UnsupportedOperationException();
        }
    }

    /** A sanction history kept in memory. */
    private static final class KeptHistory implements SanctionHistory {

        final List<SanctionHistoryEntry> entries = new ArrayList<>();

        @Override
        public void append(SanctionHistoryEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<SanctionHistoryEntry> banHistory(UUID target, int limit) {
            return List.of();
        }

        @Override
        public List<SanctionHistoryEntry> muteHistory(UUID target, int limit) {
            return List.of();
        }

        @Override
        public List<SanctionHistoryEntry> recentForTarget(UUID target, int limit) {
            return entries.stream()
                    .filter(entry -> entry.target().equals(target))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<SanctionHistoryEntry> recentByActor(UUID actor, int limit) {
            return List.of();
        }

        @Override
        public List<SanctionHistoryEntry> revocableByActor(UUID actor, int limit) {
            return List.of();
        }

        @Override
        public List<SanctionHistoryEntry> allSince(Instant threshold, int limit) {
            return List.copyOf(entries);
        }
    }

    /** An audit log that keeps one line per event, naming the event and its target. */
    private static final class RecordingAudit implements ModerationAudit {

        final List<String> lines = new ArrayList<>();

        @Override
        public void muted(
                PlayerRef actor, PlayerRef target, Optional<String> duration, boolean ok, Optional<String> reason) {
            lines.add("muted " + target.name());
        }

        @Override
        public void unmuted(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
            lines.add("unmuted " + target.name());
        }

        @Override
        public void jailed(
                PlayerRef actor,
                PlayerRef target,
                String jail,
                Optional<String> duration,
                boolean ok,
                Optional<String> reason) {
            lines.add("jailed " + target.name());
        }

        @Override
        public void unjailed(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
            lines.add("unjailed " + target.name());
        }

        @Override
        public void tempbanned(
                PlayerRef actor, PlayerRef target, String duration, boolean ok, Optional<String> reason) {
            lines.add("tempbanned " + target.name());
        }

        @Override
        public void unbanned(PlayerRef actor, PlayerRef target, boolean ok) {
            lines.add("unbanned " + target.name());
        }

        @Override
        public void warned(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
            lines.add("warned " + target.name());
        }

        @Override
        public void clearedWarns(PlayerRef actor, PlayerRef target, boolean ok, int count) {
            lines.add("cleared warns of " + target.name());
        }

        @Override
        public void kicked(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
            lines.add("kicked " + target.name());
        }

        @Override
        public void kickedAll(PlayerRef actor, int affected, Optional<String> reason) {
            lines.add("kicked all");
        }

        @Override
        public void froze(PlayerRef actor, PlayerRef target, boolean frozen, boolean ok) {
            lines.add((frozen ? "froze " : "unfroze ") + target.name());
        }

        @Override
        public void ipBanned(
                PlayerRef actor,
                String targetIp,
                Optional<UUID> target,
                Optional<String> duration,
                boolean ok,
                Optional<String> reason) {
            lines.add("ip banned " + targetIp);
        }

        @Override
        public void ipUnbanned(PlayerRef actor, String targetIp, boolean ok) {
            lines.add("ip unbanned " + targetIp);
        }

        @Override
        public void altDetected(UUID uuid, String ip, List<UUID> matchedAlts, boolean kicked) {
            lines.add("alt detected " + uuid);
        }

        @Override
        public void jailLocationDefined(PlayerRef actor, String jail) {
            lines.add("jail defined " + jail);
        }

        @Override
        public void jailLocationRemoved(PlayerRef actor, String jail) {
            lines.add("jail removed " + jail);
        }

        @Override
        public void lockdown(UUID actor, boolean enabled) {
            lines.add("lockdown " + enabled);
        }
    }

    /** A server with one player connected. */
    private record OnlyOnline(PlayerRef player) implements PlayerLookup {

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return player.name().equals(name) ? Optional.of(player) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return player.uuid().equals(uuid) ? Optional.of(player) : Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return player.uuid().equals(uuid);
        }
    }

    /** A server with nobody connected and no profile cache. */
    private static final class NobodyOnline implements PlayerLookup {

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }
}
