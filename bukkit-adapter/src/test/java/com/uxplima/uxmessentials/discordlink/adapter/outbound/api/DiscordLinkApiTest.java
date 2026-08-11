package com.uxplima.uxmessentials.discordlink.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.view.UxmDiscordLink;
import com.uxplima.uxmessentials.discordlink.application.Unlink;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published Discord binding surface: both directions of the lookup answer off the tick thread, an id that is
 * not a snowflake is a miss rather than a fault, and removing a binding that was never there says so.
 */
class DiscordLinkApiTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final DiscordId SNOWFLAKE = DiscordId.of("123456789012345678");
    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    private FakeStore store;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        store.confirm(new ConfirmedLink(ALICE.uuid(), SNOWFLAKE, WHEN));
    }

    @Test
    void readsTheBindingFromEitherEndWithoutTouchingTheTickThread() {
        QueryDoubles.InlineScheduler scheduler = new QueryDoubles.InlineScheduler();
        DiscordLinkQueries queries = new DiscordLinkQueries(store, scheduler);

        UxmDiscordLink expected = new UxmDiscordLink(ALICE.uuid(), SNOWFLAKE.value(), WHEN);
        assertThat(queries.of(ALICE.uuid()).join()).contains(expected);
        assertThat(queries.byDiscordId(SNOWFLAKE.value()).join()).contains(expected);
        assertThat(queries.isLinked(ALICE.uuid()).join()).isTrue();
    }

    @Test
    void anUnlinkedPlayerAndAnUnboundIdAreBothMisses() {
        DiscordLinkQueries queries = new DiscordLinkQueries(store, new QueryDoubles.InlineScheduler());

        assertThat(queries.of(BOB.uuid()).join()).isEmpty();
        assertThat(queries.isLinked(BOB.uuid()).join()).isFalse();
        assertThat(queries.byDiscordId("999999999999999999").join()).isEmpty();
    }

    @Test
    void anIdThatIsNotASnowflakeAnswersNobodyRatherThanFailing() {
        DiscordLinkQueries queries = new DiscordLinkQueries(store, new QueryDoubles.InlineScheduler());

        assertThat(queries.byDiscordId("not-a-snowflake").join()).isEmpty();
    }

    @Test
    void unlinkingRemovesTheBindingForAPlayerWhoIsNotOnline() {
        ActionDoubles.InlineScheduler scheduler = new ActionDoubles.InlineScheduler();
        DiscordLinkActions actions = actions(scheduler);

        assertThat(actions.unlink(ALICE.uuid()).join()).isEqualTo(UxmOutcome.ok());
        assertThat(store.findByPlayer(ALICE.uuid())).isEmpty();
        assertThat(scheduler.asyncCalls()).isOne();
    }

    @Test
    void unlinkingSomebodyWhoWasNeverLinkedIsARefusalRatherThanASilentSuccess() {
        DiscordLinkActions actions = actions(new ActionDoubles.InlineScheduler());

        UxmOutcome outcome = actions.unlink(BOB.uuid()).join();

        assertThat(outcome.failure().orElseThrow().code()).isEqualTo(UxmFailure.NOT_FOUND);
    }

    private DiscordLinkActions actions(ActionDoubles.InlineScheduler scheduler) {
        return new DiscordLinkActions(
                new Unlink(store, event -> {}),
                new QueryDoubles.MapLookup().with(ALICE).with(BOB),
                scheduler);
    }

    /** The two indexes the published query reads, kept in maps so the test says what is bound and nothing else. */
    private static final class FakeStore implements DiscordLinkStore {

        private final Map<UUID, ConfirmedLink> byPlayer = new HashMap<>();

        @Override
        public void savePending(PendingLink pending) {
            throw new UnsupportedOperationException("the published surface never issues a code");
        }

        @Override
        public Optional<PendingLink> findPendingByCode(LinkCode code) {
            return Optional.empty();
        }

        @Override
        public void deletePending(UUID player) {}

        @Override
        public void confirm(ConfirmedLink link) {
            byPlayer.put(link.player(), link);
        }

        @Override
        public Optional<ConfirmedLink> findByPlayer(UUID player) {
            return Optional.ofNullable(byPlayer.get(player));
        }

        @Override
        public Optional<ConfirmedLink> findByDiscordId(DiscordId discordId) {
            return byPlayer.values().stream()
                    .filter(link -> link.discordId().equals(discordId))
                    .findFirst();
        }

        @Override
        public boolean unlink(UUID player) {
            return byPlayer.remove(player) != null;
        }
    }
}
