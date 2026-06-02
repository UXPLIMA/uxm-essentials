package com.uxplima.uxmessentials.playerstate.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.playerstate.application.port.NearbyPlayers;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.port.StateReconciler;
import com.uxplima.uxmessentials.playerstate.domain.AirAmount;
import com.uxplima.uxmessentials.playerstate.domain.BurnDuration;
import com.uxplima.uxmessentials.playerstate.domain.ExperienceChange;
import com.uxplima.uxmessentials.playerstate.domain.FoodLevel;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.playerstate.domain.HealthLevel;
import com.uxplima.uxmessentials.playerstate.domain.PersonalTime;
import com.uxplima.uxmessentials.playerstate.domain.PersonalWeather;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.playerstate.domain.SpeedValue;
import com.uxplima.uxmessentials.playerstate.domain.event.Fed;
import com.uxplima.uxmessentials.playerstate.domain.event.GodToggled;
import com.uxplima.uxmessentials.playerstate.domain.event.Healed;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The playerstate use cases through their real implementations against in-memory fakes — the same wiring the
 * Brigadier handlers drive, minus Bukkit. It proves the headline rules of this context: a toggle mutates the
 * snapshot, reconciles, and publishes; an apply-once effect (heal/feed) changes no snapshot but still fires
 * its effect and event; a staff toggle affects the subject (not the actor); and the {@code .others} target is
 * carried through to the recorded event. The reconciler is a recorder, so the test also pins that every
 * snapshot mutation is pushed out exactly once.
 */
class PlayerStateUseCasesTest {

    private FakeStore store;
    private RecordingReconciler reconciler;
    private RecordingEffects effects;
    private RecordingEvents events;
    private PlayerStateNotifier notifier;
    private Clock clock;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        reconciler = new RecordingReconciler();
        effects = new RecordingEffects();
        events = new RecordingEvents();
        notifier = new PlayerStateNotifier(new KeyMessages(), new CapturingSink());
        clock = Clock.system(ZoneOffset.UTC);
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @Test
    void toggleGodMutatesTheSnapshotReconcilesAndPublishes() {
        ToggleGod god = new ToggleGod(store, reconciler, notifier, events, clock);

        boolean enabled = god.toggle(alice);

        assertThat(enabled).isTrue();
        assertThat(store.current(alice).god()).isTrue();
        assertThat(reconciler.reconciled).containsExactly(alice.uuid());
        assertThat(events.published).hasSize(1).first().isInstanceOf(GodToggled.class);
    }

    @Test
    void toggleGodTwiceReturnsToOff() {
        ToggleGod god = new ToggleGod(store, reconciler, notifier, events, clock);

        god.toggle(alice);
        boolean second = god.toggle(alice);

        assertThat(second).isFalse();
        assertThat(store.current(alice).god()).isFalse();
        assertThat(reconciler.reconciled).hasSize(2); // pushed out on each toggle
    }

    @Test
    void aStaffToggleAffectsTheSubjectAndRecordsBothParties() {
        ToggleFly fly = new ToggleFly(store, reconciler, notifier, events, clock);

        fly.toggleFor(alice, bob);

        assertThat(store.current(bob).fly()).isTrue();
        assertThat(store.current(alice).fly()).isFalse(); // the actor's own state is untouched
        assertThat(events.published)
                .first()
                .isInstanceOf(com.uxplima.uxmessentials.playerstate.domain.event.FlyToggled.class);
    }

    @Test
    void setGamemodePinsTheModeIntoTheSnapshot() {
        SetGamemode gamemode = new SetGamemode(store, reconciler, notifier, events, clock);

        gamemode.set(alice, GameModeRef.CREATIVE);

        assertThat(store.current(alice).gameMode()).contains(GameModeRef.CREATIVE);
        assertThat(reconciler.reconciled).containsExactly(alice.uuid());
    }

    @Test
    void setSpeedBothUpdatesWalkAndFlyAndReconcilesTwice() {
        SetSpeed speed = new SetSpeed(store, reconciler, notifier, events, clock);

        speed.setBoth(alice, alice, SpeedValue.of(6.0));

        assertThat(store.current(alice).walkSpeed().scale()).isEqualTo(6.0);
        assertThat(store.current(alice).flySpeed().scale()).isEqualTo(6.0);
        assertThat(reconciler.reconciled).hasSize(2); // one per affected speed
    }

    @Test
    void healIsApplyOnceChangingNoSnapshotButFiringTheEffect() {
        Heal heal = new Heal(effects, notifier, events, clock, true);

        heal.heal(alice);

        assertThat(effects.healed).containsExactly(alice.uuid());
        assertThat(effects.clearedEffectsFor).contains(alice.uuid()); // heal-remove-effects honoured
        assertThat(store.current(alice)).isEqualTo(PlayerStateSnapshot.initial()); // no flag changed
        assertThat(events.published).first().isInstanceOf(Healed.class);
        assertThat(reconciler.reconciled).isEmpty(); // an apply-once effect does not reconcile
    }

    @Test
    void feedIsApplyOnce() {
        Feed feed = new Feed(effects, notifier, events, clock);

        feed.feed(alice);

        assertThat(effects.fed).containsExactly(alice.uuid());
        assertThat(events.published).first().isInstanceOf(Fed.class);
    }

    @Test
    void extinguishAndSuicideAreLiveOnlyVerbs() {
        new Extinguish(effects, notifier).extinguish(alice);
        new Suicide(effects, notifier).suicide(alice);

        assertThat(effects.extinguished).containsExactly(alice.uuid());
        assertThat(effects.killed).containsExactly(alice.uuid());
        assertThat(events.published).isEmpty(); // these verbs publish no domain event
    }

    @Test
    void nightVisionReportsTheResultingState() {
        ToggleNightVision nv = new ToggleNightVision(effects, notifier);

        assertThat(nv.toggle(alice)).isTrue();
        assertThat(nv.toggle(alice)).isFalse();
    }

    @Test
    void personalTimeAndWeatherApplyThroughTheEffectsPort() {
        new SetPersonalTime(effects, notifier)
                .apply(alice, PersonalTime.parse("night").orElseThrow());
        new SetPersonalWeather(effects, notifier).apply(alice, PersonalWeather.RAIN);

        assertThat(effects.appliedTime)
                .extractingByKey(alice.uuid())
                .extracting(PersonalTime::ticks)
                .isEqualTo(14_000L);
        assertThat(effects.appliedWeather).containsEntry(alice.uuid(), PersonalWeather.RAIN);
    }

    @Test
    void nearClampsTheRadiusAndReturnsTheScan() {
        FakeNearby nearby = new FakeNearby(List.of(new NearbyPlayers.Nearby(bob, 12)));
        ListNearby near = new ListNearby(nearby, notifier);

        List<NearbyPlayers.Nearby> found = near.near(alice, 9_999_999);

        assertThat(found).extracting(n -> n.who().name()).containsExactly("Bob");
        assertThat(nearby.requestedRadius).isEqualTo(ListNearby.MAX_RADIUS); // clamped to the bound
    }

    @Test
    void experienceGiveAddsToTheCurrentTotalAndIsLiveOnly() {
        SetExperience exp = new SetExperience(effects, notifier);

        exp.apply(alice, new ExperienceChange(ExperienceChange.Op.GIVE, ExperienceChange.Unit.POINTS, 30));
        exp.apply(alice, new ExperienceChange(ExperienceChange.Op.GIVE, ExperienceChange.Unit.POINTS, 20));

        assertThat(effects.currentExp).containsEntry(alice.uuid(), 50);
        assertThat(events.published).isEmpty(); // exp publishes no domain event
    }

    @Test
    void experienceTakeIsFlooredAtZeroAndResetClears() {
        SetExperience exp = new SetExperience(effects, notifier);
        exp.apply(alice, new ExperienceChange(ExperienceChange.Op.SET, ExperienceChange.Unit.POINTS, 40));

        exp.apply(alice, new ExperienceChange(ExperienceChange.Op.TAKE, ExperienceChange.Unit.POINTS, 100));
        assertThat(effects.currentExp).containsEntry(alice.uuid(), 0);

        exp.apply(alice, new ExperienceChange(ExperienceChange.Op.SET, ExperienceChange.Unit.POINTS, 7));
        exp.apply(alice, ExperienceChange.reset());
        assertThat(effects.currentExp).containsEntry(alice.uuid(), 0);
    }

    @Test
    void airAndBurnApplyThroughTheEffectsPortClampedInTheDomain() {
        new SetAir(effects, notifier).set(alice, AirAmount.ofSeconds(15, 300));
        new Burn(effects, notifier).burn(bob, BurnDuration.ofSeconds(BurnDuration.MAX_SECONDS + 50));

        assertThat(effects.air).containsEntry(alice.uuid(), 300); // 15s = 300 ticks
        assertThat(effects.fireSeconds).containsEntry(bob.uuid(), BurnDuration.MAX_SECONDS); // clamped
    }

    @Test
    void foodLevelAppliesThroughTheEffectsPortClampedInTheDomain() {
        new SetFoodLevel(effects, notifier).set(alice, FoodLevel.of(25));

        assertThat(effects.food).containsEntry(alice.uuid(), 20); // clamped to the vanilla maximum
        assertThat(FoodLevel.of(-3).value()).isZero(); // a negative request empties the bar
    }

    @Test
    void healthAppliesThroughTheEffectsPortClampedInTheDomain() {
        new SetHealth(effects, notifier).set(alice, HealthLevel.of(10));

        assertThat(effects.health).containsEntry(alice.uuid(), 10.0); // the upper bound is the adapter's job
        assertThat(HealthLevel.of(-2).value()).isZero(); // a negative request floors at zero
    }

    @Test
    void restResetsWhenEnabledAndIsANoOpWhenDisabled() {
        new ResetRest(effects, notifier, true).reset(alice);
        assertThat(effects.restReset).containsExactly(alice.uuid());

        new ResetRest(effects, notifier, false).reset(bob);
        assertThat(effects.restReset).doesNotContain(bob.uuid()); // disabled gate blocks the effect
    }

    @Test
    void getposAndPingReadThroughTheInfoPort() {
        Position at = new Position(new WorldRef(UUID.randomUUID(), "world"), 1.0, 64.0, -3.0, 0f, 0f);
        FakeInfo info = new FakeInfo(at, 42);

        new ShowPosition(info, notifier).show(alice);
        new ShowPing(info, notifier).show(alice);

        // No exception and no mutation: read-only queries touch neither store nor reconciler.
        assertThat(reconciler.reconciled).isEmpty();
    }

    @Test
    void getposIsSilentWhenTheTargetIsOffline() {
        FakeInfo offline = new FakeInfo(null, null);

        new ShowPosition(offline, notifier).show(alice);
        new ShowPing(offline, notifier).show(alice);

        assertThat(reconciler.reconciled).isEmpty();
    }

    /** A map-backed {@link PlayerStateStore} mutated via the same compute contract as the real adapter. */
    private static final class FakeStore implements PlayerStateStore {
        private final ConcurrentHashMap<UUID, PlayerStateSnapshot> map = new ConcurrentHashMap<>();

        @Override
        public PlayerStateSnapshot current(PlayerRef who) {
            return map.computeIfAbsent(who.uuid(), id -> PlayerStateSnapshot.initial());
        }

        @Override
        public PlayerStateSnapshot update(PlayerRef who, UnaryOperator<PlayerStateSnapshot> mutator) {
            return map.compute(who.uuid(), (id, existing) -> {
                PlayerStateSnapshot base = existing == null ? PlayerStateSnapshot.initial() : existing;
                return mutator.apply(base);
            });
        }

        @Override
        public void forget(PlayerRef who) {
            map.remove(who.uuid());
        }
    }

    /** A reconciler that records each player it was asked to push a snapshot for. */
    private static final class RecordingReconciler implements StateReconciler {
        private final List<UUID> reconciled = new ArrayList<>();

        @Override
        public void reconcile(PlayerRef who, PlayerStateSnapshot snapshot) {
            reconciled.add(who.uuid());
        }
    }

    /** An effects port that records every live-only action it was asked to perform. */
    private static final class RecordingEffects implements PlayerEffects {
        private final List<UUID> healed = new ArrayList<>();
        private final List<UUID> clearedEffectsFor = new ArrayList<>();
        private final List<UUID> fed = new ArrayList<>();
        private final List<UUID> extinguished = new ArrayList<>();
        private final List<UUID> cleared = new ArrayList<>();
        private final List<UUID> killed = new ArrayList<>();
        private final Map<UUID, Boolean> nightVision = new ConcurrentHashMap<>();
        private final Map<UUID, PersonalTime> appliedTime = new ConcurrentHashMap<>();
        private final Map<UUID, PersonalWeather> appliedWeather = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> currentExp = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> air = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> fireSeconds = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> food = new ConcurrentHashMap<>();
        private final Map<UUID, Double> health = new ConcurrentHashMap<>();
        private final List<UUID> restReset = new ArrayList<>();

        @Override
        public void heal(PlayerRef who, boolean clearEffects) {
            healed.add(who.uuid());
            if (clearEffects) {
                clearedEffectsFor.add(who.uuid());
            }
        }

        @Override
        public void feed(PlayerRef who) {
            fed.add(who.uuid());
        }

        @Override
        public void extinguish(PlayerRef who) {
            extinguished.add(who.uuid());
        }

        @Override
        public void clearInventory(PlayerRef who) {
            cleared.add(who.uuid());
        }

        @Override
        public void kill(PlayerRef who) {
            killed.add(who.uuid());
        }

        @Override
        public boolean toggleNightVision(PlayerRef who) {
            return nightVision.merge(who.uuid(), Boolean.TRUE, (old, ignored) -> !old);
        }

        @Override
        public void applyTime(PlayerRef who, PersonalTime time) {
            appliedTime.put(who.uuid(), time);
        }

        @Override
        public void applyWeather(PlayerRef who, PersonalWeather weather) {
            appliedWeather.put(who.uuid(), weather);
        }

        @Override
        public void applyExperience(
                PlayerRef who,
                ExperienceChange change,
                java.util.function.Consumer<PlayerEffects.ExperienceReport> onResult) {
            int current = currentExp.getOrDefault(who.uuid(), 0);
            int next = (int) change.resolve(current);
            currentExp.put(who.uuid(), next);
            onResult.accept(new PlayerEffects.ExperienceReport(next / 100, next));
        }

        @Override
        public void setRemainingAir(PlayerRef who, AirAmount value) {
            air.put(who.uuid(), value.ticks());
        }

        @Override
        public void setFire(PlayerRef who, BurnDuration duration) {
            fireSeconds.put(who.uuid(), duration.seconds());
        }

        @Override
        public void setFoodLevel(PlayerRef who, FoodLevel value) {
            food.put(who.uuid(), value.value());
        }

        @Override
        public void setHealth(PlayerRef who, HealthLevel value) {
            health.put(who.uuid(), value.value());
        }

        @Override
        public void resetRest(PlayerRef who) {
            restReset.add(who.uuid());
        }
    }

    /** A read-only info port returning fixed position and ping. */
    private static final class FakeInfo implements PlayerInfo {
        private final @Nullable Position position;
        private final @Nullable Integer ping;

        FakeInfo(@Nullable Position position, @Nullable Integer ping) {
            this.position = position;
            this.ping = ping;
        }

        @Override
        public java.util.Optional<Position> positionOf(PlayerRef who) {
            return java.util.Optional.ofNullable(position);
        }

        @Override
        public java.util.Optional<Integer> pingOf(PlayerRef who) {
            return java.util.Optional.ofNullable(ping);
        }
    }

    /** A nearby scan that returns a fixed list and records the radius it was asked for. */
    private static final class FakeNearby implements NearbyPlayers {
        private final List<Nearby> result;
        private int requestedRadius;

        FakeNearby(List<Nearby> result) {
            this.result = result;
        }

        @Override
        public List<Nearby> within(PlayerRef viewer, int radius) {
            requestedRadius = radius;
            return result;
        }
    }

    private static final class RecordingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
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
