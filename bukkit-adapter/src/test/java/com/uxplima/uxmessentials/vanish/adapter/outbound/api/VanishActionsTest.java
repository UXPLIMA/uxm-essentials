package com.uxplima.uxmessentials.vanish.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import com.uxplima.uxmessentials.vanish.application.port.VanishBuffs;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published vanish action: hiding somebody hides them for real, asking twice changes nothing, and there is
 * nobody to hide when nobody is here.
 */
class VanishActionsTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    private InMemoryVanishStore store;
    private RecordingView view;
    private ActionDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        store = new InMemoryVanishStore();
        view = new RecordingView();
        scheduler = new ActionDoubles.InlineScheduler();
    }

    @Test
    void hidingAPlayerHidesThemFromEverybodyBelowTheirLevel() {
        assertThat(actions().setVanished(ALICE.uuid(), true).join().succeeded()).isTrue();

        assertThat(store.isVanished(ALICE.uuid())).isTrue();
        assertThat(view.hidden).containsExactly(ALICE.uuid());
    }

    @Test
    void hidingAPlayerWhoIsAlreadyHiddenChangesNothing() {
        actions().setVanished(ALICE.uuid(), true).join();

        actions().setVanished(ALICE.uuid(), true).join();

        assertThat(store.isVanished(ALICE.uuid())).isTrue();
        assertThat(view.hidden)
                .as("a second hide that ran the toggle would have revealed them instead")
                .containsExactly(ALICE.uuid());
    }

    @Test
    void showingThemAgainRevealsThem() {
        actions().setVanished(ALICE.uuid(), true).join();

        actions().setVanished(ALICE.uuid(), false).join();

        assertThat(store.isVanished(ALICE.uuid())).isFalse();
        assertThat(view.revealed).containsExactly(ALICE.uuid());
    }

    @Test
    void aPlayerWhoIsNotHereIsSaidToBeOffline() {
        UxmOutcome outcome = actions().setVanished(UUID.randomUUID(), true).join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.PLAYER_OFFLINE)).isTrue();
    }

    @Test
    void theWriteRunsOnThePlayersOwnThread() {
        actions().setVanished(ALICE.uuid(), true).join();

        assertThat(scheduler.entityCalls()).isEqualTo(1);
    }

    private VanishActions actions() {
        ToggleVanish toggle = new ToggleVanish(
                store,
                view,
                new FirstLevel(),
                ActionDoubles.silentNotifier(),
                new NoBuffs(),
                VanishBus.disabled(),
                event -> {});
        return new VanishActions(toggle, new QueryDoubles.MapLookup().with(ALICE), scheduler);
    }

    /** Remembers who was hidden from whom, which is the part of vanish a store cannot show. */
    private static final class RecordingView implements VanishView {

        private final List<UUID> hidden = new ArrayList<>();
        private final List<UUID> revealed = new ArrayList<>();

        @Override
        public void hide(PlayerRef who, VanishLevel level) {
            hidden.add(who.uuid());
        }

        @Override
        public void reveal(PlayerRef who) {
            revealed.add(who.uuid());
        }
    }

    private static final class FirstLevel implements VanishLevelResolver {

        @Override
        public VanishLevel useLevel(PlayerRef who) {
            return VanishLevel.of(1);
        }

        @Override
        public int seeLevel(PlayerRef who) {
            return 1;
        }
    }

    private static final class NoBuffs implements VanishBuffs {

        @Override
        public void apply(PlayerRef who) {}

        @Override
        public void clear(PlayerRef who) {}
    }
}
