package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The player-warps command paths through the real use cases against an in-memory repository and a recording
 * teleporter — the same wiring the Brigadier handlers drive, minus Bukkit. It proves that {@code /setpwarp}
 * persists a warp keyed per owner and re-anchors in place, that a set past the resolved per-owner limit is
 * refused, that {@code /pwarp} delegates execution to the teleport context, that ownership and the public flag
 * gate cross-owner use, that {@code /pwarp del} removes a warp, that {@code /pwarps} lists own warps and only a
 * player's public warps, and that the visibility toggles flip the public flag.
 */
class PlayerWarpCommandPathTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakePlayerWarpRepository repository;
    private RecordingTeleporter teleporter;
    private PlayerWarpNotifier notifier;
    private DomainEventPublisher events;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        repository = new FakePlayerWarpRepository();
        teleporter = new RecordingTeleporter();
        notifier = new PlayerWarpNotifier(new KeyMessages(), new CapturingSink());
        events = new CapturingEvents();
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @Test
    void setPwarpPersistsTheWarpForItsOwner() {
        Result<Unit, PlayerWarpError> result = setWarp(10).set(alice, PlayerWarpName.of("base"), at(10, 64, 20));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.exists(alice, PlayerWarpName.of("base"))).isTrue();
        assertThat(repository.exists(bob, PlayerWarpName.of("base"))).isFalse();
    }

    @Test
    void setPwarpOnAnExistingNameReanchorsInPlace() {
        setWarp(10).set(alice, PlayerWarpName.of("base"), at(0, 0, 0));

        setWarp(10).set(alice, PlayerWarpName.of("base"), at(100, 70, 100));

        assertThat(repository.ownedBy(alice)).hasSize(1);
        assertThat(repository
                        .find(alice, PlayerWarpName.of("base"))
                        .orElseThrow()
                        .location()
                        .blockX())
                .isEqualTo(100);
    }

    @Test
    void setPwarpPastTheResolvedLimitIsRefused() {
        setWarp(2).set(alice, PlayerWarpName.of("one"), at(0, 0, 0));
        setWarp(2).set(alice, PlayerWarpName.of("two"), at(1, 1, 1));

        Result<Unit, PlayerWarpError> third = setWarp(2).set(alice, PlayerWarpName.of("three"), at(2, 2, 2));

        assertThat(third.errorOrThrow()).isEqualTo(PlayerWarpError.LIMIT_REACHED);
        assertThat(repository.count(alice)).isEqualTo(2);
    }

    @Test
    void pwarpDelegatesExecutionToTheTeleportContext() {
        setWarp(10).set(alice, PlayerWarpName.of("base"), at(7, 64, 7));

        Result<Unit, PlayerWarpError> result = usePwarp().use(alice, PlayerWarpName.of("base"));

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.hops).isEqualTo(1);
        assertThat(teleporter.lastWarp.name()).isEqualTo(PlayerWarpName.of("base"));
    }

    @Test
    void anotherPlayersPrivateWarpIsRefused() {
        setWarp(10).set(alice, PlayerWarpName.of("base"), at(0, 0, 0));

        Result<Unit, PlayerWarpError> result = usePwarp().useFor(bob, alice, PlayerWarpName.of("base"));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_PUBLIC);
        assertThat(teleporter.hops).isZero();
    }

    @Test
    void aPublicWarpIsUsableByAnotherPlayer() {
        setWarp(10).set(alice, PlayerWarpName.of("base"), at(0, 0, 0));
        visibility().setPublic(alice, PlayerWarpName.of("base"));

        Result<Unit, PlayerWarpError> result = usePwarp().useFor(bob, alice, PlayerWarpName.of("base"));

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void aMissingWarpIsRejected() {
        Result<Unit, PlayerWarpError> result = usePwarp().use(alice, PlayerWarpName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
        assertThat(teleporter.hops).isZero();
    }

    @Test
    void delPwarpRemovesTheWarp() {
        setWarp(10).set(alice, PlayerWarpName.of("base"), at(0, 0, 0));

        Result<Unit, PlayerWarpError> result =
                new DelPlayerWarp(repository, notifier, events).delete(alice, PlayerWarpName.of("base"));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.exists(alice, PlayerWarpName.of("base"))).isFalse();
    }

    @Test
    void pwarpsOwnListsOwnedWarps() {
        setWarp(10).set(alice, PlayerWarpName.of("base"), at(0, 0, 0));
        setWarp(10).set(alice, PlayerWarpName.of("farm"), at(1, 1, 1));

        List<PlayerWarp> owned = new ListPlayerWarps(repository, notifier).own(alice);

        assertThat(owned.stream().map(w -> w.name().value())).containsExactly("base", "farm");
    }

    @Test
    void pwarpsForAnotherPlayerListsOnlyTheirPublicWarps() {
        setWarp(10).set(alice, PlayerWarpName.of("base"), at(0, 0, 0));
        setWarp(10).set(alice, PlayerWarpName.of("secret"), at(1, 1, 1));
        visibility().setPublic(alice, PlayerWarpName.of("base"));

        List<PlayerWarp> shown = new ListPlayerWarps(repository, notifier).publicOf(bob, alice, "Alice");

        assertThat(shown.stream().map(w -> w.name().value())).containsExactly("base");
    }

    @Test
    void crossOwnerEntriesUseTheOtherOwnerEntryKeyCarryingTheOwner() {
        setWarp(10).set(alice, PlayerWarpName.of("base"), at(0, 0, 0));
        visibility().setPublic(alice, PlayerWarpName.of("base"));
        RecordingSink sink = new RecordingSink();
        PlayerWarpNotifier recording = new PlayerWarpNotifier(new KeyMessages(), sink);

        new ListPlayerWarps(repository, recording).publicOf(bob, alice, "Alice");

        // The cross-owner entry must resolve through the other-owner key (whose template runs /pwarp <warp>
        // <owner>), never the own-list entry (which would click-run /pwarp <warp> and hit the viewer's warp).
        assertThat(sink.delivered).contains(PlayerwarpsMessageKey.PWARP_LIST_OTHER_ENTRY.key());
        assertThat(sink.delivered).doesNotContain(PlayerwarpsMessageKey.PWARP_LIST_ENTRY.key());
    }

    @Test
    void visibilityTogglesFlipThePublicFlag() {
        setWarp(10).set(alice, PlayerWarpName.of("base"), at(0, 0, 0));

        visibility().setPublic(alice, PlayerWarpName.of("base"));
        assertThat(repository
                        .find(alice, PlayerWarpName.of("base"))
                        .orElseThrow()
                        .isPublic())
                .isTrue();

        visibility().setPrivate(alice, PlayerWarpName.of("base"));
        assertThat(repository
                        .find(alice, PlayerWarpName.of("base"))
                        .orElseThrow()
                        .isPublic())
                .isFalse();
    }

    private SetPlayerWarp setWarp(int limit) {
        PlayerWarpQuota quota = new PlayerWarpQuota(new StubPermissions(limit), limit);
        return new SetPlayerWarp(repository, quota, notifier, events, Clock.system(ZoneOffset.UTC), List.of());
    }

    private UsePlayerWarp usePwarp() {
        return new UsePlayerWarp(repository, teleporter, notifier, pos -> true, new StubPermissions(10));
    }

    private SetPlayerWarpVisibility visibility() {
        return new SetPlayerWarpVisibility(repository, notifier);
    }

    private static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }

    /** A map-backed {@link PlayerWarpRepository} keyed per owner, keeping warps in insertion order. */
    private static final class FakePlayerWarpRepository implements PlayerWarpRepository {
        private final Map<UUID, Map<String, PlayerWarp>> byOwner = new LinkedHashMap<>();

        @Override
        public Optional<PlayerWarp> find(PlayerRef owner, PlayerWarpName name) {
            return Optional.ofNullable(set(owner).get(name.value()));
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            return List.copyOf(set(owner).values());
        }

        @Override
        public List<PlayerWarp> publicOf(PlayerRef owner) {
            List<PlayerWarp> shown = new ArrayList<>();
            for (PlayerWarp warp : set(owner).values()) {
                if (warp.isPublic()) {
                    shown.add(warp);
                }
            }
            return List.copyOf(shown);
        }

        @Override
        public int count(PlayerRef owner) {
            return set(owner).size();
        }

        @Override
        public boolean exists(PlayerRef owner, PlayerWarpName name) {
            return set(owner).containsKey(name.value());
        }

        @Override
        public void save(PlayerWarp warp) {
            byOwner.computeIfAbsent(warp.owner().uuid(), u -> new LinkedHashMap<>())
                    .put(warp.name().value(), warp);
        }

        @Override
        public void delete(PlayerRef owner, PlayerWarpName name) {
            set(owner).remove(name.value());
        }

        @Override
        public void recordVisit(PlayerRef owner, PlayerWarpName name) {
            PlayerWarp warp = set(owner).get(name.value());
            if (warp != null) {
                set(owner).put(name.value(), warp.incrementedVisitors());
            }
        }

        @Override
        public void rate(PlayerRef owner, PlayerWarpName name, java.util.UUID player, double rating) {}

        @Override
        public double averageRating(PlayerRef owner, PlayerWarpName name) {
            return 0.0;
        }

        private Map<String, PlayerWarp> set(PlayerRef owner) {
            return byOwner.getOrDefault(owner.uuid(), Map.of());
        }
    }

    private static final class RecordingTeleporter implements PlayerWarpTeleporter {
        int hops;
        private PlayerWarp lastWarp = PlayerWarp.create(
                new PlayerRef(new UUID(0L, 0L), "none"),
                PlayerWarpName.of("none"),
                Position.of(WORLD, 0, 0, 0),
                java.time.Instant.EPOCH);

        @Override
        public void teleportTo(PlayerRef who, PlayerWarp warp) {
            hops++;
            lastWarp = warp;
        }
    }

    /** A stub permissions port resolving every quota to the constructor limit. */
    private static final class StubPermissions implements Permissions {
        private final long limit;

        StubPermissions(long limit) {
            this.limit = limit;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @org.jspecify.annotations.Nullable WorldRef world, long fallback) {
            return QuotaResult.limited(limit);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // discarded: feedback delivery is not under test here
        }
    }

    /** A sink that records every rendered text it is handed, for asserting which message key was emitted. */
    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    private static final class CapturingEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {
            // discarded: event publication is asserted elsewhere
        }
    }
}
