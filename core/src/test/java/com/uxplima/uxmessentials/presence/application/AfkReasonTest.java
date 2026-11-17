package com.uxplima.uxmessentials.presence.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A reason given for being away is kept as the player typed it, so the API and placeholders read it as written, and
 * handed to the lines other players read unchanged: the line escapes every value it is filled with, so escaping it here
 * as well would show the escapes.
 */
class AfkReasonTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);
    private static final PlayerRef MALLORY = new PlayerRef(UUID.randomUUID(), "Mallory");
    private static final PlayerRef READER = new PlayerRef(UUID.randomUUID(), "Reader");
    private static final String REASON = "<click:run_command:'/op Mallory'>brb</click>";

    private final Store store = new Store();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final MarkAfk markAfk =
            new MarkAfk(store, () -> List.of(MALLORY, READER), notifier.notifier(), new RecordingEvents(), CLOCK);

    @Test
    void theReasonIsKeptAsTypedAndHandedToTheLineUnchanged() {
        markAfk.toggle(MALLORY, Optional.of(REASON));

        assertThat(store.current(MALLORY).afkReason()).contains(REASON);
        assertThat(notifier.sent())
                .filteredOn(sent -> sent.viewer().equals(READER))
                .singleElement()
                .satisfies(sent -> assertThat(sent.placeholders().get("reason")).isEqualTo(REASON));
    }

    /** Presence kept in memory. */
    private static final class Store implements PresenceStore {

        private final Map<PlayerRef, PlayerPresence> byPlayer = new HashMap<>();

        @Override
        public PlayerPresence current(PlayerRef who) {
            return byPlayer.getOrDefault(who, PlayerPresence.active(CLOCK.instant()));
        }

        @Override
        public PlayerPresence update(PlayerRef who, UnaryOperator<PlayerPresence> mutator) {
            PlayerPresence next = mutator.apply(current(who));
            byPlayer.put(who, next);
            return next;
        }

        @Override
        public void forget(PlayerRef who) {
            byPlayer.remove(who);
        }

        @Override
        public Map<PlayerRef, PlayerPresence> snapshotAll() {
            return Map.copyOf(byPlayer);
        }
    }
}
