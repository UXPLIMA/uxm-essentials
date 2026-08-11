package com.uxplima.uxmessentials.scoreboard.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderer;
import com.uxplima.uxmessentials.scoreboard.application.ToggleScoreboard;
import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.domain.event.ScoreboardVisibilityToggled;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The published sidebar surface: the read tells a preference apart from a player who is away, and the writes run the
 * same use case {@code /scoreboard} runs rather than reaching past it.
 */
class ScoreboardApiTest {

    private ServerMock server;
    private PlayerMock alice;
    private PlayerRef who;
    private FakeVisibility visibility;
    private ScoreboardRenderer renderer;
    private ActionDoubles.RecordingEvents events;
    private ActionDoubles.InlineScheduler writeScheduler;
    private ScoreboardActions actions;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        alice = server.addPlayer("Alice");
        who = new PlayerRef(alice.getUniqueId(), alice.getName());
        visibility = new FakeVisibility();
        renderer = mock(ScoreboardRenderer.class);
        events = new ActionDoubles.RecordingEvents();
        writeScheduler = new ActionDoubles.InlineScheduler();
        actions = new ScoreboardActions(
                new ToggleScoreboard(visibility, ActionDoubles.silentNotifier(), events),
                visibility,
                renderer,
                new QueryDoubles.MapLookup().with(who),
                writeScheduler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPlayerWhoNeverTouchedItIsShown() {
        QueryDoubles.InlineScheduler reads = new QueryDoubles.InlineScheduler();

        assertThat(queries(reads).hidden(who.uuid()).join()).contains(false);
        assertThat(reads.entityCalls()).isOne();
    }

    @Test
    void aPlayerWhoIsAwayHasNoReadablePreferenceRatherThanADefault() {
        assertThat(queries(new QueryDoubles.InlineScheduler())
                        .hidden(UUID.randomUUID())
                        .join())
                .isEmpty();
    }

    @Test
    void hidingPutsTheBoardAwayThroughTheSameUseCaseTheCommandUses() {
        UxmOutcome outcome = actions.hide(who.uuid()).join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(visibility.hidden(who)).isTrue();
        assertThat(events.published()).hasAtLeastOneElementOfType(ScoreboardVisibilityToggled.class);
        verify(renderer).clear(alice);
        assertThat(writeScheduler.entityCalls()).isOne();
    }

    @Test
    void showingBringsItBackAndDrawsItAgain() {
        actions.hide(who.uuid()).join();

        assertThat(actions.show(who.uuid()).join().succeeded()).isTrue();
        assertThat(visibility.hidden(who)).isFalse();
        verify(renderer).renderFor(alice);
    }

    @Test
    void askingForTheStateTheyAreAlreadyInIsRefusedRatherThanFlipped() {
        UxmOutcome alreadyShown = actions.show(who.uuid()).join();

        assertThat(alreadyShown.failureOrThrow().code()).isEqualTo(UxmFailure.ALREADY_IN_STATE);
        assertThat(visibility.hidden(who)).isFalse();
        assertThat(events.published()).isEmpty();
        verify(renderer, never()).renderFor(alice);
    }

    @Test
    void refreshDrawsTheBoardWithoutTouchingThePreference() {
        assertThat(actions.refresh(who.uuid()).join().succeeded()).isTrue();

        verify(renderer).renderFor(alice);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void aPlayerWhoLeftIsAnsweredOfflineRatherThanLeftHanging() {
        writeScheduler.retire(who);

        assertThat(actions.refresh(who.uuid()).join().failureOrThrow().code()).isEqualTo(UxmFailure.PLAYER_OFFLINE);
        assertThat(actions.hide(who.uuid()).join().failureOrThrow().code()).isEqualTo(UxmFailure.PLAYER_OFFLINE);
    }

    private ScoreboardQueries queries(QueryDoubles.InlineScheduler reads) {
        return new ScoreboardQueries(visibility, new QueryDoubles.MapLookup().with(who), reads);
    }

    /** The preference, kept in a set rather than in PDC, since what is being tested is the decision around it. */
    private static final class FakeVisibility implements ScoreboardVisibilityStore {

        private final Set<UUID> hidden = new HashSet<>();

        @Override
        public boolean hidden(PlayerRef who) {
            return hidden.contains(who.uuid());
        }

        @Override
        public boolean toggle(PlayerRef who) {
            if (hidden.remove(who.uuid())) {
                return false;
            }
            hidden.add(who.uuid());
            return true;
        }

        @Override
        public void forget(PlayerRef who) {}
    }
}
