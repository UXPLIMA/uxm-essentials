package com.uxplima.uxmessentials.persistence.invrollback;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.application.port.PendingRestores;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kept restores come back whole, in the order they were kept, only for their owner, and are handed out once: a second
 * server taking after the first gets nothing.
 */
@EveryBackend
class JooqPendingRestoresTest {

    private static final UUID IVY = UUID.fromString("00000000-0000-0000-0000-0000000001e1");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final Instant DIED = Instant.parse("2030-01-01T10:00:00Z");

    private DSLContext dsl;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
    }

    @Test
    void keptRestoresComeBackWholeInOrderAndOnlyForTheirOwner() {
        PendingRestores store = new JooqPendingRestores(dsl, ticking());
        Snapshot death = Snapshot.capture(IVY, SnapshotCause.DEATH, DIED, new byte[] {1, 2, 3});
        Snapshot logout = Snapshot.capture(IVY, SnapshotCause.LOGOUT, DIED.plusSeconds(60), new byte[0]);
        Snapshot sams = Snapshot.capture(SAM, SnapshotCause.DEATH, DIED, new byte[] {7});
        store.keep(IVY, death);
        store.keep(SAM, sams);
        store.keep(IVY, logout);

        assertThat(store.take(IVY)).containsExactly(death, logout);
        assertThat(store.take(SAM)).containsExactly(sams);
    }

    @Test
    void aRestoreIsHandedOutOnceAcrossServers() {
        PendingRestores lobby = new JooqPendingRestores(dsl, ticking());
        PendingRestores survival = new JooqPendingRestores(dsl, ticking());
        Snapshot death = Snapshot.capture(IVY, SnapshotCause.DEATH, DIED, new byte[] {1, 2, 3});
        lobby.keep(IVY, death);

        assertThat(survival.take(IVY)).containsExactly(death);
        assertThat(lobby.take(IVY)).isEmpty();
    }

    /** A clock a second further on each time it is read, so the kept order is the order of the keeps. */
    private static Clock ticking() {
        return new Clock() {
            private long millis = DIED.toEpochMilli();

            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                millis += 1000;
                return Instant.ofEpochMilli(millis);
            }
        };
    }
}
