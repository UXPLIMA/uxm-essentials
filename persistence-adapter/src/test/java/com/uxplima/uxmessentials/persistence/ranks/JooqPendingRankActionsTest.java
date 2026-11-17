package com.uxplima.uxmessentials.persistence.ranks;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.ranks.application.port.PendingRankActions;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kept rank actions come back in the order they were kept, only for their owner, and are handed out once: a second
 * server taking after the first gets nothing.
 */
@EveryBackend
class JooqPendingRankActionsTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    /** Every write in the same millisecond, so only the kept order can tell the batches apart. */
    private static final Clock FROZEN = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);

    private DSLContext dsl;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
    }

    @Test
    void keptLinesComeBackInTheOrderTheyWereKeptAndOnlyForTheirOwner() {
        PendingRankActions store = new JooqPendingRankActions(dsl, FROZEN);
        store.keep(ALEX, List.of("console lp user {uuid} parent set citizen", "message citizen"));
        store.keep(SAM, List.of("message sam"));
        store.keep(ALEX, List.of("message vip"));

        assertThat(store.take(ALEX))
                .containsExactly("console lp user {uuid} parent set citizen", "message citizen", "message vip");
        assertThat(store.take(SAM)).containsExactly("message sam");
    }

    @Test
    void linesAreHandedOutOnceAcrossServers() {
        PendingRankActions lobby = new JooqPendingRankActions(dsl, FROZEN);
        PendingRankActions survival = new JooqPendingRankActions(dsl, FROZEN);
        lobby.keep(ALEX, List.of("message citizen"));

        List<String> first = survival.take(ALEX);
        List<String> second = lobby.take(ALEX);

        assertThat(first).containsExactly("message citizen");
        assertThat(second).isEmpty();
    }
}
