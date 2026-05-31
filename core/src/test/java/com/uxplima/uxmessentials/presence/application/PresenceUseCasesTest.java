package com.uxplima.uxmessentials.presence.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.presence.application.port.PresenceAudience;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.application.port.VisibilityApplier;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.presence.domain.event.ReturnedFromAfk;
import com.uxplima.uxmessentials.presence.domain.event.VanishToggled;
import com.uxplima.uxmessentials.presence.domain.event.WentAfk;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The presence use cases through their real implementations against in-memory fakes — the same wiring the
 * Brigadier handlers, the activity listeners, and the AFK sweep drive, minus Bukkit. It pins the headline rules:
 * {@code /afk} toggles AFK and publishes the matching event; the sweep's auto-mark flips an idle player exactly
 * once; activity clears AFK and only announces a return when the player was actually AFK; vanish flips the flag,
 * drives the visibility applier, and publishes; and the cross-context vanish query honours the see-node.
 */
class PresenceUseCasesTest {

    private FakeStore store;
    private FakeAudience audience;
    private RecordingVisibility visibility;
    private RecordingEvents events;
    private PresenceNotifier notifier;
    private FakePermissions permissions;
    private Clock clock;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        audience = new FakeAudience();
        visibility = new RecordingVisibility();
        events = new RecordingEvents();
        notifier = new PresenceNotifier(new KeyMessages(), new CapturingSink());
        permissions = new FakePermissions();
        clock = Clock.system(ZoneOffset.UTC);
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
        audience.players.add(alice);
        audience.players.add(bob);
    }

    @Test
    void afkToggleEntersAfkWithReasonAndPublishesWentAfk() {
        MarkAfk markAfk = new MarkAfk(store, audience, notifier, events, clock);

        boolean afk = markAfk.toggle(alice, Optional.of("lunch"));

        assertThat(afk).isTrue();
        assertThat(store.current(alice).afk()).isTrue();
        assertThat(store.current(alice).afkReason()).contains("lunch");
        WentAfk event = (WentAfk) onlyEvent();
        assertThat(event.subject()).isEqualTo(alice);
        assertThat(event.automatic()).isFalse();
        assertThat(event.reason()).contains("lunch");
    }

    @Test
    void afkToggleTwiceReturnsAndPublishesReturnedFromAfk() {
        MarkAfk markAfk = new MarkAfk(store, audience, notifier, events, clock);

        markAfk.toggle(alice, Optional.empty());
        boolean afk = markAfk.toggle(alice, Optional.empty());

        assertThat(afk).isFalse();
        assertThat(store.current(alice).afk()).isFalse();
        assertThat(events.published).last().isInstanceOf(ReturnedFromAfk.class);
    }

    @Test
    void autoMarkFlipsAnActivePlayerOnce() {
        MarkAfk markAfk = new MarkAfk(store, audience, notifier, events, clock);

        boolean firstFlip = markAfk.markAuto(alice);
        boolean secondFlip = markAfk.markAuto(alice);

        assertThat(firstFlip).isTrue();
        assertThat(secondFlip).isFalse(); // already AFK, no re-flip
        assertThat(store.current(alice).afk()).isTrue();
        WentAfk event = (WentAfk) onlyEvent();
        assertThat(event.automatic()).isTrue();
        assertThat(event.reason()).isEmpty();
    }

    @Test
    void activityOnAnAfkPlayerReturnsThemAndAnnounces() {
        MarkAfk markAfk = new MarkAfk(store, audience, notifier, events, clock);
        ClearAfkOnActivity clearAfk = new ClearAfkOnActivity(store, audience, notifier, events, clock);
        markAfk.markAuto(alice);
        events.published.clear();

        clearAfk.recordActivity(alice);

        assertThat(store.current(alice).afk()).isFalse();
        assertThat(events.published).singleElement().isInstanceOf(ReturnedFromAfk.class);
    }

    @Test
    void activityOnAnActivePlayerJustRestampsWithNoEvent() {
        ClearAfkOnActivity clearAfk = new ClearAfkOnActivity(store, audience, notifier, events, clock);

        clearAfk.recordActivity(alice);

        assertThat(store.current(alice).afk()).isFalse();
        assertThat(events.published).isEmpty(); // no return event when the player was never AFK
    }

    @Test
    void vanishToggleHidesDrivesVisibilityAndPublishes() {
        ToggleVanish toggleVanish = new ToggleVanish(store, visibility, notifier, events, clock);

        boolean vanished = toggleVanish.toggle(alice);

        assertThat(vanished).isTrue();
        assertThat(store.current(alice).vanished()).isTrue();
        assertThat(visibility.hidden).containsExactly(alice.uuid());
        assertThat(visibility.revealed).isEmpty();
        VanishToggled event = (VanishToggled) onlyEvent();
        assertThat(event.vanished()).isTrue();
    }

    @Test
    void vanishToggleTwiceRevealsAgain() {
        ToggleVanish toggleVanish = new ToggleVanish(store, visibility, notifier, events, clock);

        toggleVanish.toggle(alice);
        boolean vanished = toggleVanish.toggle(alice);

        assertThat(vanished).isFalse();
        assertThat(store.current(alice).vanished()).isFalse();
        assertThat(visibility.revealed).containsExactly(alice.uuid());
    }

    @Test
    void resolveVisibilityHidesAVanishedTargetFromAViewerWithoutTheSeeNode() {
        store.update(alice, PlayerPresence::vanish);
        ResolveVisibility resolve = new ResolveVisibility(store, permissions);

        assertThat(resolve.isHiddenFrom(bob, alice)).isTrue();
    }

    @Test
    void resolveVisibilityShowsAVanishedTargetToAViewerHoldingTheSeeNode() {
        store.update(alice, PlayerPresence::vanish);
        permissions.grant(bob, "uxmessentials.vanish.see");
        ResolveVisibility resolve = new ResolveVisibility(store, permissions);

        assertThat(resolve.isHiddenFrom(bob, alice)).isFalse();
    }

    @Test
    void resolveVisibilityNeverHidesANonVanishedTargetOrTheViewerThemselves() {
        ResolveVisibility resolve = new ResolveVisibility(store, permissions);

        assertThat(resolve.isHiddenFrom(bob, alice)).isFalse(); // alice not vanished
        store.update(alice, PlayerPresence::vanish);
        assertThat(resolve.isHiddenFrom(alice, alice)).isFalse(); // a player always sees themselves
    }

    private DomainEvent onlyEvent() {
        assertThat(events.published).hasSize(1);
        return events.published.get(0);
    }

    /** A map-backed {@link PresenceStore} mutated via the same compute contract as the real adapter. */
    private static final class FakeStore implements PresenceStore {
        private final ConcurrentHashMap<UUID, Entry> map = new ConcurrentHashMap<>();
        private final Clock clock = Clock.system(ZoneOffset.UTC);

        @Override
        public PlayerPresence current(PlayerRef who) {
            return map.computeIfAbsent(who.uuid(), id -> new Entry(who, PlayerPresence.active(clock.instant())))
                    .presence();
        }

        @Override
        public PlayerPresence update(PlayerRef who, UnaryOperator<PlayerPresence> mutator) {
            return map.compute(who.uuid(), (id, existing) -> {
                        PlayerPresence base =
                                existing == null ? PlayerPresence.active(clock.instant()) : existing.presence();
                        return new Entry(who, mutator.apply(base));
                    })
                    .presence();
        }

        @Override
        public void forget(PlayerRef who) {
            map.remove(who.uuid());
        }

        @Override
        public Map<PlayerRef, PlayerPresence> snapshotAll() {
            Map<PlayerRef, PlayerPresence> copy = new java.util.HashMap<>();
            map.values().forEach(entry -> copy.put(entry.who(), entry.presence()));
            return Map.copyOf(copy);
        }

        private record Entry(PlayerRef who, PlayerPresence presence) {}
    }

    /** An audience returning a fixed online set. */
    private static final class FakeAudience implements PresenceAudience {
        private final List<PlayerRef> players = new ArrayList<>();

        @Override
        public List<PlayerRef> online() {
            return List.copyOf(players);
        }
    }

    /** A visibility applier that records each hide/reveal/reconcile it was asked to perform. */
    private static final class RecordingVisibility implements VisibilityApplier {
        private final List<UUID> hidden = new ArrayList<>();
        private final List<UUID> revealed = new ArrayList<>();

        @Override
        public void hide(PlayerRef who) {
            hidden.add(who.uuid());
        }

        @Override
        public void reveal(PlayerRef who) {
            revealed.add(who.uuid());
        }

        @Override
        public void reconcileOnJoin(PlayerRef who, boolean vanished) {
            // not exercised by these use-case tests
        }
    }

    private static final class RecordingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    /** A permissions fake granting only the nodes explicitly handed to a player. */
    private static final class FakePermissions implements Permissions {
        private final Set<String> grants = new HashSet<>();

        void grant(PlayerRef who, String node) {
            grants.add(who.uuid() + "|" + node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return grants.contains(who.uuid() + "|" + node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.unlimited();
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
}
