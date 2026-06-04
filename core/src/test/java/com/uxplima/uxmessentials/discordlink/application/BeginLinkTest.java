package com.uxplima.uxmessentials.discordlink.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class BeginLinkTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    private final InMemoryDiscordLinkStore store = new InMemoryDiscordLinkStore();

    @Test
    void issuesACodeAndPersistsAPendingRowWithTheTtlExpiry() {
        BeginLink begin = new BeginLink(store, fixedClock(), new java.util.Random(7L), TTL);

        LinkCode code = begin.begin(ALICE);

        PendingLink row = store.pendingOf(ALICE.uuid()).orElseThrow();
        assertThat(row.code()).isEqualTo(code);
        assertThat(row.player()).isEqualTo(ALICE.uuid());
        assertThat(row.expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void aSecondBeginReplacesThePlayersPreviousCode() {
        BeginLink begin = new BeginLink(store, fixedClock(), new java.util.Random(7L), TTL);

        LinkCode first = begin.begin(ALICE);
        LinkCode second = begin.begin(ALICE);

        // Only one pending row for the player, holding the latest code; the old code resolves to nothing.
        assertThat(store.pendingOf(ALICE.uuid()).orElseThrow().code()).isEqualTo(second);
        assertThat(store.findPendingByCode(second)).isPresent();
        if (!first.equals(second)) {
            assertThat(store.findPendingByCode(first)).isEmpty();
        }
    }

    @Test
    void retriesWhenADrawnCodeCollidesWithAnotherPlayersOutstandingCode() {
        // A generator that yields the same first draw twice then a different one, so the first begin and the
        // start of the second collide and the second must retry to a fresh code.
        RandomGenerator scripted = new RandomGenerator() {
            private final int[] sequence = {0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1};
            private int i = 0;

            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public int nextInt(int bound) {
                return sequence[Math.min(i++, sequence.length - 1)] % bound;
            }
        };
        BeginLink begin = new BeginLink(store, fixedClock(), scripted, TTL);
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");

        LinkCode alice = begin.begin(ALICE);
        LinkCode bobCode = begin.begin(bob);

        assertThat(bobCode).isNotEqualTo(alice);
        assertThat(store.findPendingByCode(alice).orElseThrow().player()).isEqualTo(ALICE.uuid());
        assertThat(store.findPendingByCode(bobCode).orElseThrow().player()).isEqualTo(bob.uuid());
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
