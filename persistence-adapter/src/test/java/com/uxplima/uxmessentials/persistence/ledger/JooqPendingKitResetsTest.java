package com.uxplima.uxmessentials.persistence.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.uxmessentials.kits.application.port.PendingKitResets;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kept kit resets come back only for their owner, and are handed out once: a second take gets nothing, so a reset is
 * applied on one join only.
 */
@EveryBackend
class JooqPendingKitResetsTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    private static final Clock FROZEN = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);

    private DSLContext dsl;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
    }

    @Test
    void keptResetsComeBackOnlyForTheirOwner() {
        PendingKitResets store = new JooqPendingKitResets(dsl, FROZEN);
        store.keep(ALEX, "starter");
        store.keep(SAM, PendingKitResets.ALL);
        store.keep(ALEX, "vip");

        assertThat(store.take(ALEX)).containsExactlyInAnyOrder("starter", "vip");
        assertThat(store.take(SAM)).containsExactly(PendingKitResets.ALL);
    }

    @Test
    void aResetIsHandedOutOnce() {
        PendingKitResets store = new JooqPendingKitResets(dsl, FROZEN);
        store.keep(ALEX, "starter");

        assertThat(store.take(ALEX)).containsExactly("starter");
        assertThat(store.take(ALEX)).isEmpty();
    }

    @Test
    void aResetKeptTwiceComesBackOnce() {
        PendingKitResets store = new JooqPendingKitResets(dsl, FROZEN);
        store.keep(ALEX, "starter");
        store.keep(ALEX, "starter");

        assertThat(store.take(ALEX)).containsExactly("starter");
        assertThat(store.take(ALEX)).isEmpty();
    }
}
