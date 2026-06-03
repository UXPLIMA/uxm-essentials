package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;
import org.junit.jupiter.api.Test;

/**
 * {@code /tpalist} reads the pending queue aimed at the viewer and reports it: nothing pending sends the
 * shared none-pending key; a non-empty queue sends a header then one entry per requester, oldest first.
 * The registry and the notifier sink are hand-rolled fakes so the rule is pinned in pure {@code :core}.
 */
class ListPendingRequestsTest {

    private static final Instant TTL = Instant.parse("2026-05-30T12:01:00Z");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "Target");
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    @Test
    void anEmptyQueueSendsTheNonePendingKey() {
        FakeRegistry registry = new FakeRegistry(List.of());
        CapturingSink sink = new CapturingSink();
        ListPendingRequests listing = listing(registry, sink);

        listing.list(TARGET);

        assertThat(sink.sent).containsExactly(TeleportMessageKey.TPA_NONE_PENDING.key());
    }

    @Test
    void aQueueSendsAHeaderThenOneEntryPerRequesterOldestFirst() {
        TeleportRequest fromAlice = TeleportRequest.open(ALICE, TARGET, RequestDirection.TO_TARGET, TTL)
                .request();
        TeleportRequest fromBob = TeleportRequest.open(BOB, TARGET, RequestDirection.TO_TARGET, TTL)
                .request();
        FakeRegistry registry = new FakeRegistry(List.of(fromAlice, fromBob));
        CapturingSink sink = new CapturingSink();
        ListPendingRequests listing = listing(registry, sink);

        listing.list(TARGET);

        assertThat(sink.sent)
                .containsExactly(
                        TeleportMessageKey.TPA_LIST_HEADER.key(),
                        TeleportMessageKey.TPA_LIST_ENTRY.key() + "|Alice",
                        TeleportMessageKey.TPA_LIST_ENTRY.key() + "|Bob");
    }

    private static ListPendingRequests listing(RequestRegistry registry, MessageSink sink) {
        return new ListPendingRequests(registry, new PlayerNotifier(new KeyEchoMessages(), sink));
    }

    private static final class FakeRegistry implements RequestRegistry {
        private final List<TeleportRequest> pending;

        FakeRegistry(List<TeleportRequest> pending) {
            this.pending = List.copyOf(pending);
        }

        @Override
        public void store(TeleportRequest request) {}

        @Override
        public Optional<TeleportRequest> byId(RequestId id) {
            return Optional.empty();
        }

        @Override
        public List<TeleportRequest> pendingFor(PlayerRef target) {
            return pending;
        }

        @Override
        public Optional<TeleportRequest> outgoing(PlayerRef requester) {
            return Optional.empty();
        }

        @Override
        public void remove(RequestId id) {}
    }

    /** Folds the key and any {@code player} placeholder into the rendered string for assertion. */
    private static final class KeyEchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            String player = placeholders.get("player");
            return player == null ? key.key() : key.key() + "|" + player;
        }
    }

    /** Records the rendered text of every delivery, in order. */
    private static final class CapturingSink implements MessageSink {
        final List<String> sent = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            sent.add(renderedText);
        }
    }
}
