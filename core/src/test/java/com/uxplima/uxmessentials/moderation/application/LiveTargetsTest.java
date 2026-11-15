package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.networkpresence.NetworkRoster;
import com.uxplima.uxmessentials.shared.application.networkpresence.RosterStamp;
import com.uxplima.uxmessentials.shared.application.networkpresence.TrackNetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresenceBroadcast;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A kick, a freeze or a /seen on a player connected to another backend says where they are instead of acting on
 * nothing, and writes no history, audit or broadcast.
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

    private final List<String> writes = new ArrayList<>();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final NetworkRoster presence = new NetworkRoster("survival-1", CLOCK, Duration.ofSeconds(90));

    LiveTargetsTest() {
        TrackNetworkPresence track = new TrackNetworkPresence(presence, NetworkPresenceBroadcast.NONE, NO_NAMES, CLOCK);
        track.remoteJoined("lobby-1", new RosterStamp(1L, 1L), REMOTE);
        track.joined(LOCAL);
    }

    @Test
    void aKickOnAPlayerOnAnotherBackendNamesTheBackendAndWritesNothing() {
        Kick kick = new Kick(
                recording(Sanctions.class),
                new ModerationGuard(new FakePermissions()),
                notifier.notifier(),
                recording(ModerationAudit.class),
                new SanctionHistoryRecorder(recording(SanctionHistory.class), CLOCK),
                recording(SanctionBroadcast.class),
                presence);

        assertThat(kick.kick(STAFF, REMOTE, Optional.of("spam"), false).errorOrThrow())
                .isEqualTo(ModerationError.TARGET_ELSEWHERE);
        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(
                        STAFF, "moderation.target-elsewhere", Map.of("player", "Steve", "server", "lobby-1")));
        assertThat(writes).isEmpty();
    }

    @Test
    void aKickOnAPlayerHereStillKicks() {
        Kick kick = new Kick(
                recording(Sanctions.class),
                new ModerationGuard(new FakePermissions()),
                notifier.notifier(),
                recording(ModerationAudit.class),
                new SanctionHistoryRecorder(recording(SanctionHistory.class), CLOCK),
                recording(SanctionBroadcast.class),
                presence);

        assertThat(kick.kick(STAFF, LOCAL, Optional.empty(), true).isErr()).isFalse();
        assertThat(writes).contains("Sanctions.kick");
    }

    @Test
    void aFreezeAndAnUnfreezeOnAPlayerOnAnotherBackendAreRefused() {
        Freeze freeze = new Freeze(
                recording(Sanctions.class),
                new ModerationGuard(new FakePermissions()),
                notifier.notifier(),
                recording(ModerationAudit.class),
                presence);

        assertThat(freeze.freeze(STAFF, REMOTE).errorOrThrow()).isEqualTo(ModerationError.TARGET_ELSEWHERE);
        assertThat(freeze.unfreeze(STAFF, REMOTE).errorOrThrow()).isEqualTo(ModerationError.TARGET_ELSEWHERE);
        assertThat(notifier.keys()).containsExactly("moderation.target-elsewhere", "moderation.target-elsewhere");
        assertThat(writes).isEmpty();
    }

    @Test
    void seenReportsAPlayerOnAnotherBackendAsOnlineThere() {
        Seen seen = new Seen(
                recording(ModerationRepository.class),
                new IpAlts(recording(IpHistoryStore.class), recording(IpTokens.class)),
                recording(PlayerLookup.class),
                notifier.notifier(),
                false,
                CLOCK,
                presence);

        seen.seen(STAFF, REMOTE);

        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(
                        STAFF, "moderation.seen.online-elsewhere", Map.of("player", "Steve", "server", "lobby-1")));
    }

    /** A fake of {@code port} that records every call into {@link #writes} and answers empty. */
    private <T> T recording(Class<T> port) {
        Object proxy = Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[] {port}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> self == args[0];
                    case "hashCode" -> System.identityHashCode(self);
                    default -> port.getSimpleName();
                };
            }
            writes.add(port.getSimpleName() + "." + method.getName());
            Class<?> returns = method.getReturnType();
            if (returns == boolean.class) {
                return false;
            }
            if (returns == int.class) {
                return 0;
            }
            if (returns == long.class) {
                return 0L;
            }
            if (returns == Optional.class) {
                return Optional.empty();
            }
            if (returns == List.class) {
                return List.of();
            }
            return null;
        });
        return port.cast(proxy);
    }
}
