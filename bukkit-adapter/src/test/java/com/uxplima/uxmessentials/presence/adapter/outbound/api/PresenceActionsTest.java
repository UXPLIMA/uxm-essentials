package com.uxplima.uxmessentials.presence.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.presence.adapter.outbound.InMemoryPresenceStore;
import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.port.PresenceAudience;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published presence action: a setter that sets, a reason that reaches the AFK list, and no presence
 * invented for a player who is not here.
 */
class PresenceActionsTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private InMemoryPresenceStore store;
    private ActionDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        store = new InMemoryPresenceStore(CLOCK, uuid -> false);
        scheduler = new ActionDoubles.InlineScheduler();
    }

    @Test
    void markingAPlayerAwayTwiceLeavesThemAway() {
        // The command toggles, so a setter that just called it would bring back the player it was asked to
        // keep away, and announce the return to everybody.
        assertThat(actions().setAfk(ALICE.uuid(), true).join().succeeded()).isTrue();
        actions().setAfk(ALICE.uuid(), true).join();

        assertThat(queries().isAfk(ALICE.uuid())).isTrue();
    }

    @Test
    void bringingThemBackClearsIt() {
        actions().setAfk(ALICE.uuid(), true).join();

        actions().setAfk(ALICE.uuid(), false).join();

        assertThat(queries().isAfk(ALICE.uuid())).isFalse();
    }

    @Test
    void aReasonIsWhatOthersSeeNextToTheirName() {
        actions().setAfk(ALICE.uuid(), "making tea").join();

        assertThat(queries().of(ALICE.uuid()).orElseThrow().afkReason()).contains("making tea");
    }

    @Test
    void aPlayerWhoIsNotHereIsSaidToBeOfflineAndGetsNoPresence() {
        UUID stranger = UUID.randomUUID();

        UxmOutcome outcome = actions().setAfk(stranger, true).join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.PLAYER_OFFLINE)).isTrue();
        assertThat(store.snapshotAll()).isEmpty();
    }

    private PresenceActions actions() {
        MarkAfk markAfk = new MarkAfk(
                store, new OnlyAlice(), ActionDoubles.silentNotifier(), new ActionDoubles.RecordingEvents(), CLOCK);
        return new PresenceActions(markAfk, store, new QueryDoubles.MapLookup().with(ALICE), scheduler);
    }

    private PresenceQueries queries() {
        return new PresenceQueries(store);
    }

    /** One player to announce to, which is enough for the announcement path to run. */
    private static final class OnlyAlice implements PresenceAudience {

        @Override
        public List<PlayerRef> online() {
            return List.of(ALICE);
        }
    }
}
