package com.uxplima.uxmessentials.playerstate.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.view.UxmGameMode;
import com.uxplima.uxmessentials.api.view.UxmPlayerState;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.InMemoryPlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.Feed;
import com.uxplima.uxmessentials.playerstate.application.Heal;
import com.uxplima.uxmessentials.playerstate.application.SetGamemode;
import com.uxplima.uxmessentials.playerstate.application.SetSpeed;
import com.uxplima.uxmessentials.playerstate.application.ToggleFly;
import com.uxplima.uxmessentials.playerstate.application.ToggleGod;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.application.port.StateReconciler;
import com.uxplima.uxmessentials.playerstate.domain.AirAmount;
import com.uxplima.uxmessentials.playerstate.domain.BurnDuration;
import com.uxplima.uxmessentials.playerstate.domain.ExperienceChange;
import com.uxplima.uxmessentials.playerstate.domain.FoodLevel;
import com.uxplima.uxmessentials.playerstate.domain.FreezeDuration;
import com.uxplima.uxmessentials.playerstate.domain.GlowColor;
import com.uxplima.uxmessentials.playerstate.domain.HealthLevel;
import com.uxplima.uxmessentials.playerstate.domain.PersonalTime;
import com.uxplima.uxmessentials.playerstate.domain.PersonalWeather;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published player-state actions: a setter sets rather than flips, what a plugin writes is what the query
 * reads back, and none of it touches a player who is not here.
 */
class PlayerStateActionsTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private InMemoryPlayerStateStore store;
    private RecordingEffects effects;
    private ActionDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        store = new InMemoryPlayerStateStore();
        effects = new RecordingEffects();
        scheduler = new ActionDoubles.InlineScheduler();
    }

    @Test
    void turningGodModeOnTwiceLeavesItOn() {
        // The module models this as a toggle, which is right for a keystroke and wrong for an API: a plugin
        // granting protection twice must not end up removing it.
        assertThat(actions().setGodMode(ALICE.uuid(), true).join().succeeded()).isTrue();
        assertThat(actions().setGodMode(ALICE.uuid(), true).join().succeeded()).isTrue();

        assertThat(store.current(ALICE).god()).isTrue();
    }

    @Test
    void turningFlightOffLeavesItOff() {
        actions().setFlying(ALICE.uuid(), true).join();

        actions().setFlying(ALICE.uuid(), false).join();
        actions().setFlying(ALICE.uuid(), false).join();

        assertThat(store.current(ALICE).fly()).isFalse();
    }

    @Test
    void theGameModeAskedForIsTheGameModeHeld() {
        actions().setGameMode(ALICE.uuid(), UxmGameMode.CREATIVE).join();

        assertThat(queries().of(ALICE.uuid()).orElseThrow().gameMode()).contains(UxmGameMode.CREATIVE);
    }

    @Test
    void aSpeedWrittenIsTheSpeedReadBack() {
        actions().setWalkSpeed(ALICE.uuid(), 0.4f).join();
        actions().setFlySpeed(ALICE.uuid(), 0.5f).join();

        UxmPlayerState state = queries().of(ALICE.uuid()).orElseThrow();
        assertThat(state.walkSpeed()).isEqualTo(0.4f);
        assertThat(state.flySpeed()).isEqualTo(0.5f);
    }

    @Test
    void healingAndFeedingReachTheLivePlayer() {
        actions().heal(ALICE.uuid()).join();
        actions().feed(ALICE.uuid()).join();

        assertThat(effects.applied).containsExactly("heal", "feed");
    }

    @Test
    void aPlayerWhoIsNotHereIsSaidToBeOffline() {
        UxmOutcome outcome = actions().setGodMode(UUID.randomUUID(), true).join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.PLAYER_OFFLINE)).isTrue();
    }

    @Test
    void aPlayerWhoLeavesBeforeTheHopCompletesTheFutureAnyway() {
        scheduler.retire(ALICE);

        UxmOutcome outcome = actions().setGodMode(ALICE.uuid(), true).join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.PLAYER_OFFLINE)).isTrue();
    }

    @Test
    void everyWriteRunsOnThePlayersOwnThread() {
        actions().setGodMode(ALICE.uuid(), true).join();
        actions().heal(ALICE.uuid()).join();

        assertThat(scheduler.entityCalls()).isEqualTo(2);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    private PlayerStateActions actions() {
        StateReconciler reconciler = (who, snapshot) -> {};
        ActionDoubles.RecordingEvents events = new ActionDoubles.RecordingEvents();
        PlayerStateApiWrites writes = new PlayerStateApiWrites(
                new ToggleGod(store, reconciler, ActionDoubles.silentNotifier(), events, CLOCK),
                new ToggleFly(store, reconciler, ActionDoubles.silentNotifier(), events, CLOCK),
                new SetGamemode(store, reconciler, ActionDoubles.silentNotifier(), events, CLOCK),
                new SetSpeed(store, reconciler, ActionDoubles.silentNotifier(), events, CLOCK),
                new Heal(effects, ActionDoubles.silentNotifier(), events, CLOCK, true),
                new Feed(effects, ActionDoubles.silentNotifier(), events, CLOCK));
        return new PlayerStateActions(writes, store, new QueryDoubles.MapLookup().with(ALICE), scheduler, "TestPlugin");
    }

    private PlayerStateQueries queries() {
        return new PlayerStateQueries(store, new QueryDoubles.MapLookup().with(ALICE));
    }

    /** Remembers which effects reached the player, since heal and feed leave nothing in the snapshot. */
    private static final class RecordingEffects implements PlayerEffects {

        private final List<String> applied = new ArrayList<>();

        @Override
        public void heal(PlayerRef who, boolean clearEffects) {
            applied.add("heal");
        }

        @Override
        public void feed(PlayerRef who) {
            applied.add("feed");
        }

        @Override
        public boolean toggleGlow(PlayerRef who) {
            return false;
        }

        @Override
        public void setGlow(PlayerRef who, GlowColor colour) {}

        @Override
        public boolean toggleNightVision(PlayerRef who) {
            return false;
        }

        @Override
        public void extinguish(PlayerRef who) {}

        @Override
        public void clearInventory(PlayerRef who) {}

        @Override
        public void kill(PlayerRef who) {}

        @Override
        public void applyTime(PlayerRef who, PersonalTime time) {}

        @Override
        public void applyWeather(PlayerRef who, PersonalWeather weather) {}

        @Override
        public void applyExperience(
                PlayerRef who,
                ExperienceChange change,
                java.util.function.Consumer<PlayerEffects.ExperienceReport> onResult) {}

        @Override
        public void setRemainingAir(PlayerRef who, AirAmount air) {}

        @Override
        public void setFire(PlayerRef who, BurnDuration duration) {}

        @Override
        public void setFreeze(PlayerRef who, FreezeDuration duration) {}

        @Override
        public void setFoodLevel(PlayerRef who, FoodLevel food) {}

        @Override
        public void setHealth(PlayerRef who, HealthLevel value) {}

        @Override
        public void resetRest(PlayerRef who) {}
    }
}
