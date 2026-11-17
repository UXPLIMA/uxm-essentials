package com.uxplima.uxmessentials.persistence.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What each backend saw of an exemption, through the real migrations, as two backends on one database write it: a
 * grant seen on either counts, each backend's latest reading replaces its earlier one, and a late, older write does
 * not.
 */
@EveryBackend
class JooqExemptionSightingsTest {

    private static final UUID ADA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    private JooqExemptionSightings lobby;
    private JooqExemptionSightings survival;

    @BeforeEach
    void open(TestDatabase database) {
        lobby = new JooqExemptionSightings(database.dsl());
        survival = new JooqExemptionSightings(database.dsl());
    }

    @Test
    void aGrantOneBackendSawCountsForEveryBackend() {
        lobby.record(ADA, "lobby", false, NOW);
        survival.record(ADA, "survival", true, NOW);

        assertThat(lobby.heldAnywhere(ADA)).isTrue();
        assertThat(lobby.heldAnywhere(BOB)).as("never seen").isFalse();
    }

    @Test
    void aBackendsLatestReadingReplacesItsEarlierOne() {
        survival.record(ADA, "survival", true, NOW);

        survival.record(ADA, "survival", false, NOW.plusSeconds(5));

        assertThat(lobby.heldAnywhere(ADA)).isFalse();
    }

    @Test
    void aLateOlderReadingDoesNotReplaceANewerOne() {
        survival.record(ADA, "survival", true, NOW.plusSeconds(5));

        survival.record(ADA, "survival", false, NOW);

        assertThat(lobby.heldAnywhere(ADA)).isTrue();
    }
}
